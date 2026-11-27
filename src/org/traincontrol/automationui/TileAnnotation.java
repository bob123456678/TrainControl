package org.traincontrol.automationui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.util.ImageUtil;

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
     * What a sensor has been designated as, drawn as a badge on its tile.  The SHAPE says what turning means (circle:
     * trains do not turn, diamond: may, square: always), the SIZE whether it is a station (big) or a passing point
     * (small).  Blue means autonomy uses it, gray it does not (FR-103), orange that nothing can pass.
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

        /** A station trains must stop at, none running through (MT-1017): a dot at the icon's center. */
        private final boolean stopOnly;

        public Badge(boolean station, boolean terminus, boolean reversing, boolean parking,
            boolean named)
        {
            this(station, terminus, reversing, parking, named, null, null);
        }

        public Badge(boolean station, boolean terminus, boolean reversing, boolean parking,
            boolean named, Side a, Side b)
        {
            this(station, terminus, reversing, parking, named, a, b, false);
        }

        public Badge(boolean station, boolean terminus, boolean reversing, boolean parking,
            boolean named, Side a, Side b, boolean optional)
        {
            this(station, terminus, reversing, parking, named, a, b, optional, false);
        }

        public Badge(boolean station, boolean terminus, boolean reversing, boolean parking,
            boolean named, Side a, Side b, boolean optional, boolean shut)
        {
            this(station, terminus, reversing, parking, named, a, b, optional, shut, null);
        }

        public Badge(boolean station, boolean terminus, boolean reversing, boolean parking,
            boolean named, Side a, Side b, boolean optional, boolean shut, Side deadEnd)
        {
            this(station, terminus, reversing, parking, named, a, b, optional, shut, deadEnd, false);
        }

        public Badge(boolean station, boolean terminus, boolean reversing, boolean parking,
            boolean named, Side a, Side b, boolean optional, boolean shut, Side deadEnd, boolean stopOnly)
        {
            this.stopOnly = stopOnly;
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

        /** Whether turning around here is a choice rather than compulsory. */
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

        public boolean isStopOnly()
        {
            return stopOnly;
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
                && shut == other.shut && stopOnly == other.stopOnly
                && a == other.a && b == other.b && deadEnd == other.deadEnd;
        }

        @Override
        public int hashCode()
        {
            return (station ? 1 : 0) + (terminus ? 2 : 0) + (reversing ? 4 : 0)
                + (parking ? 8 : 0) + (named ? 16 : 0) + (optional ? 32 : 0) + (shut ? 64 : 0)
                + (stopOnly ? 128 : 0);
        }

        @Override
        public String toString()
        {
            if (isImpassable()) return "shut" + (named ? "" : " (unnamed)");

            return (station ? (parking ? "parking" : "station") : "point")
                + (terminus ? (optional ? " may turn" : " terminus") : "")
                + (reversing ? " reversing" : "")
                + (stopOnly ? " stop-only" : "")
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
    private List<Arrival> arrivals;

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

        public Side getFrom()
        {
            return from;
        }

        public Side getTo()
        {
            return to;
        }
    }

    public TileAnnotation(List<Mark> marks, int length, boolean selected)
    {
        this(marks, length, selected, null, false);
    }

    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored)
    {
        this(marks, length, selected, badge, ignored, false);
    }

    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored, boolean curved)
    {
        this(marks, length, selected, badge, ignored, curved, false);
    }

    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored, boolean curved, boolean portal)
    {
        this(marks, length, selected, badge, ignored, curved, portal, null);
    }

    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored, boolean curved, boolean portal, List<Trace> traces)
    {
        this(marks, length, selected, badge, ignored, curved, portal, traces, false);
    }

    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored, boolean curved, boolean portal, List<Trace> traces, boolean blockedOnly)
    {
        this(marks, length, selected, badge, ignored, curved, portal, traces, blockedOnly, null);
    }

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

    /** Also draws the green allowed-direction arrows, with no wash (FR-110: Display Allowed Directions). */
    public TileAnnotation withAllowedDirections()
    {
        this.allowedToo = true;
        return this;
    }

    private boolean allowedToo = false;
    private volatile boolean hideOpenGreen = false;

    /** Draws no green on a station's own road while trains may leave it every way: on the diagram that is the default, so it says nothing. */
    public TileAnnotation withoutOpenGreen()
    {
        this.hideOpenGreen = true;
        return this;
    }

    /** Draws the station's icon at this share of its size, for a picture whose arrows are read first (FR-111). */
    public TileAnnotation withSmallerIcon(float share)
    {
        this.iconShare = share;
        return this;
    }

    private float iconShare = 1f;

    /** Draws no ways in, for a picture that draws its own (FR-111). */
    public TileAnnotation withoutArrivals()
    {
        this.arrivals = Collections.emptyList();
        return this;
    }

    /**
     * Grays the square as one autonomy takes no notice of, but still draws its length and outline (OB-353); no arrows.
     */
    public TileAnnotation withNoStation()
    {
        this.stationless = true;
        return this;
    }

    private boolean stationless = false;

    /** Leaves an ignored square empty: neither its art nor the graying is drawn (FR-197). */
    public TileAnnotation leftEmpty()
    {
        this.empty = true;
        return this;
    }

    private volatile boolean empty = false;

    public boolean isLeftEmpty()
    {
        return empty;
    }

    /** Has the square's own art drawn without color, as the editor draws its signals and lamps. */
    public TileAnnotation inGray()
    {
        this.gray = true;
        return this;
    }

    private boolean gray = false;

    public boolean isInGray()
    {
        return gray;
    }

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

    /** Draws no arrow at one side (null for none), where a two-square crossing's squares meet (OB-322). */
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
     * `isBlank`, `equals` and `hashCode` as well as `paint` (FR-005): plain track has nothing else to say.
     */
    public TileAnnotation needsALength()
    {
        this.unmeasured = true;

        return this;
    }

    private boolean unmeasured = false;

    /**
     * How Test a Path and Why not Moving? mark a square (FR-159): a thick orange band on what can be clicked, blue on
     * what was (OB-395), and Why not Moving?'s answer on the stations.
     */
    public static enum ToolMark
    {
        /** Can be clicked: a station for Test a Path, a square with a train for Why not Moving?. */
        CLICKABLE,

        /** The square clicked: Test a Path's start, or the train Why not Moving? answers for. */
        PICKED,

        /** The train can go here now. */
        CAN_GO,

        /** The train cannot be sent here right now. */
        NOT_NOW,

        /** Only a train sent by hand can go here now (FR-221). */
        BY_HAND,

        /** Picked to be set with others in Station Settings (FR-052), in a color no other mark uses. */
        TOGETHER,

        /** Can be picked for Bulk Edit (MT-920): a gray outline that shows against the grid, no wash. */
        PICKABLE
    }

    /** Marks this square for a tool; carried through `isBlank`, `equals` and `hashCode` as `needsALength` is. */
    public TileAnnotation markedByATool(ToolMark mark)
    {
        this.toolMark = mark;

        return this;
    }

    private ToolMark toolMark = null;

    /** How a tool marks this square, or null. */
    public ToolMark getToolMark()
    {
        return toolMark;
    }

    private static final Color MARK_BLUE = new Color(25, 118, 210);
    private static final Color MARK_GREEN = new Color(34, 150, 60);
    private static final Color MARK_GRAY = new Color(120, 120, 120);
    private static final float MARK_FILL_ALPHA = 0.24f;

    /** The mark's wash, under the badge so its icon stays sharp; none where it cannot go now or is clickable. */
    private void paintToolFill(Graphics2D g, int width, int height)
    {
        if (toolMark == ToolMark.NOT_NOW || toolMark == ToolMark.CLICKABLE || toolMark == ToolMark.PICKABLE) return;

        Composite before = g.getComposite();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, MARK_FILL_ALPHA));
        g.setColor(markColour());
        g.fillRoundRect(1, 1, width - 2, height - 2, width / 4, height / 4);

        g.setComposite(before);
    }

    /**
     * The mark's border, over everything but a selection's: dashed where it cannot go now; where it can be clicked, a
     * band in the selection's orange at the square's edge (OB-395), under a tested route's line; picked, a band too.
     */
    private void paintToolBorder(Graphics2D g, int width, int height)
    {
        if (toolMark == ToolMark.PICKABLE)
        {
            g.setStroke(new BasicStroke(2f));
            g.setColor(markColour());
            g.drawRect(1, 1, width - 3, height - 3);

            return;
        }

        if (toolMark == ToolMark.CLICKABLE || toolMark == ToolMark.TOGETHER)
        {
            // Thicker than a selection's, a tenth of the square, so it reads at a glance across the diagram
            float thick = Math.max(3f, Math.min(width, height) / 10f);

            g.setStroke(new BasicStroke(thick));
            g.setColor(markColour());
            g.draw(new Rectangle2D.Float(thick / 2, thick / 2, width - thick, height - thick));

            return;
        }

        float wide = Math.max(1.5f, Math.min(width, height) / 15f);

        g.setStroke(toolMark == ToolMark.NOT_NOW
            ? new BasicStroke(wide, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {wide * 2, wide * 2}, 0f)
            : new BasicStroke(wide));
        g.setColor(markColour());
        g.drawRoundRect(1, 1, width - 3, height - 3, width / 4, height / 4);
    }

    /** The tool's border, then the selection's outline over it; the clickable band went under the lines. */
    private void paintOutlines(Graphics2D g, int width, int height)
    {
        if (toolMark != null && toolMark != ToolMark.CLICKABLE) paintToolBorder(g, width, height);

        if (selected) paintSelectionOutline(g, width, height);
    }

    private static void paintSelectionOutline(Graphics2D g, int width, int height)
    {
        g.setStroke(new BasicStroke(2f));
        g.setColor(SELECTED);
        g.drawRect(1, 1, width - 3, height - 3);
    }

    /** Darker than the grid's light gray, which an outline on it cannot be told from. */
    private static final Color PICKABLE_GRAY = new Color(128, 128, 128);

    /** TOGETHER's teal, which no other mark on the diagram uses. */
    private static final Color MARK_TEAL = new Color(0, 140, 140);

    private Color markColour()
    {
        switch (toolMark)
        {
            case CLICKABLE: return SELECTED;
            case PICKED: return MARK_BLUE;
            case CAN_GO: return MARK_GREEN;
            case BY_HAND: return TRACE_MANUAL;
            case TOGETHER: return MARK_TEAL;
            case PICKABLE: return PICKABLE_GRAY;
            default: return MARK_GRAY;
        }
    }

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
        Composite before = g.getComposite();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, UNMEASURED_ALPHA));
        g.setColor(UNMEASURED_COLOUR);
        g.fillRect(0, 0, width, height);

        g.setComposite(before);
    }

    public boolean isIgnored()
    {
        return ignored;
    }

    /** Whether the arrows follow a curve's chord. */
    public boolean isCurved()
    {
        return curved;
    }

    /** The look of a square autonomy takes no notice of: washed out and hatched; leaves g's composite changed. */
    private static void paintGreyedOut(Graphics2D g, int width, int height)
    {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, IGNORED_ALPHA));
        g.setColor(IGNORED);
        g.fillRect(0, 0, width, height);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        g.setColor(HATCH);
        g.setStroke(new BasicStroke(1f));

        for (int at = -height; at < width; at += 5) g.drawLine(at, 0, at + height, height);
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
     * belongs here too, or it is never painted when it is the ONLY thing on a square (FR-005).
     */
    public boolean isBlank()
    {
        return marks.isEmpty() && length < 0 && !selected && badge == null && !ignored && !stationless
            && traces.isEmpty() && arrivals.isEmpty() && !occupied && !unmeasured && toolMark == null && !gray;
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
        paint(g, width, height, alreadyFaded ? new Rectangle(0, 0, width, height) : null);
    }

    /**
     * The same, told exactly WHICH part of the tile is faint (null if none): a double curve is two roads in one tile
     * and a train covers one, so the road with no train keeps its wash.
     */
    public void paint(Graphics2D g, int width, int height, Shape alreadyFaint)
    {
        if (isBlank()) return;

        Object oldHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke oldStroke = g.getStroke();
        Color oldColor = g.getColor();
        Font oldFont = g.getFont();

        Composite oldComposite = g.getComposite();

        try
        {
            ImageUtil.antialias(g);

            // A square autonomy takes no notice of is grayed out and nothing else is drawn on it; anything drawn
            // would invite a click.
            if (ignored)
            {
                if (!empty) paintGreyedOut(g, width, height);

                // The tested route still shows over the graying: it answers the user's question.
                if (!traces.isEmpty())
                {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

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

            if (unmeasured) paintUnmeasured(g, width, height);

            if (toolMark != null) paintToolFill(g, width, height);

            // The badge goes under the arrows (MT-710); `paintBadgeOverRun` draws it again over them.
            if (badge != null) paintBadge(g, width, height);

            paintArrows(g, width, height);

            paintArrivals(g, width, height);

            // Over the badge (MT-099).
            if (occupied) paintTrainMark(g, width, height);

            if (length >= 0) paintLength(g, width, height);

            if (toolMark == ToolMark.CLICKABLE) paintToolBorder(g, width, height);

            paintTraces(g, width, height);

            paintOutlines(g, width, height);
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
    private Set<Side> cornerOf(Side side, int width, int height)
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

        return EnumSet.of(side, toward);
    }

    /** Whether this square is out of service (nothing may pass or stop), so it carries no arrows. */
    private boolean outOfService()
    {
        return badge != null && badge.isImpassable();
    }

    /**
     * Whether nudging an arrival arrow this way puts it over the tile's own track (OB-175): on a curve one corner is
     * where the track is.
     */
    private boolean offsetCollidesWithTrack(Side arriving, double px, double py)
    {
        if (arriving == null || marks.isEmpty()) return false;

        Side toward;

        if (Math.abs(px) > Math.abs(py))
        {
            toward = px > 0 ? Side.E : Side.W;
        }
        else
        {
            toward = py > 0 ? Side.S : Side.N;
        }

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

        double wing = Math.max(2.5, span / 7.0);
        double depth = Math.max(3.0, span / 5.0);

        double offset = span / 4.0;

        // Nearer the middle where two share a corner (MT-714), so chevrons on a double curve do not touch.
        Map<Set<Side>, Integer> corners = new java.util.HashMap<>();

        for (Arrival arrival : arrivals)
        {
            Set<Side> corner = cornerOf(arrival.getSide(), width, height);

            if (corner != null) corners.put(corner, corners.containsKey(corner) ? corners.get(corner) + 1 : 1);
        }

        g.setStroke(new BasicStroke(Math.max(1.2f, span / 22f),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (Arrival arrival : arrivals)
        {
            int[] at = midpoint(arrival.getSide(), width, height);

            if (at == null) continue;

            Set<Side> corner = cornerOf(arrival.getSide(), width, height);

            double along = corner != null && corners.get(corner) > 1 ? span / 5.0 : offset;

            double dx = width / 2.0 - at[0];
            double dy = height / 2.0 - at[1];
            double length = Math.sqrt(dx * dx + dy * dy);

            if (length < 1) continue;

            dx /= length;
            dy /= length;

            double px = -dy;
            double py = dx;

            // Away from this tile's own track (OB-175): on a curve the run occupies one corner.
            if (offsetCollidesWithTrack(arrival.getSide(), px, py))
            {
                px = -px;
                py = -py;
            }

            double baseX = at[0] + dx * 1.5 + px * along;
            double baseY = at[1] + dy * 1.5 + py * along;

            paintArrival(g, head(baseX, baseY, dx, dy, px, py, wing, depth), arrival.isAllowed(), span);
        }
    }

    private static Path2D head(double baseX, double baseY, double dx, double dy, double px, double py,
        double wing, double depth)
    {
        Path2D head = new Path2D.Double();

        head.moveTo(baseX + px * wing, baseY + py * wing);
        head.lineTo(baseX + dx * depth, baseY + dy * depth);
        head.lineTo(baseX - px * wing, baseY - py * wing);

        return head;
    }

    /**
     * The arrival chevron's shape for a square of this size, its base at the given point, pointing along `heading` (a
     * unit vector), for a picture that draws one off the diagram's place (FR-111).
     */
    public static Path2D arrivalShape(double baseX, double baseY, double[] heading, int span)
    {
        return head(baseX, baseY, heading[0], heading[1], -heading[1], heading[0], Math.max(2.5, span / 7.0),
            Math.max(3.0, span / 5.0));
    }

    /** Draws an arrival chevron: filled where trains may stop coming that way, a gray open chevron where not. */
    public static void paintArrival(Graphics2D g, Path2D head, boolean allowed, int span)
    {
        Stroke was = g.getStroke();

        if (allowed)
        {
            head.closePath();

            g.setStroke(new BasicStroke(Math.max(1.2f, span / 22f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            g.setColor(ARRIVAL);
            g.fill(head);

            g.setColor(ARRIVAL_EDGE);
            g.draw(head);
        }
        else
        {
            // THE CHEVRON ALONE, ITS ARMS SCALED WITH THE SQUARE, IN GRAY (OB-326): strokes through a hollow one read as a triangle.
            g.setColor(ARRIVAL_BARRED);
            g.setStroke(new BasicStroke((float) Math.max(2.0, span / 14.0), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
            g.draw(head);
        }

        g.setStroke(was);
    }

    /**
     * Draws one arrow per SIDE of the tile, not two per route: a switch's branches meet at its toe, and a side is
     * permitted if ANY route through the tile permits it.
     */
    private void paintArrows(Graphics2D g, int width, int height)
    {
        // NONE ON A SQUARE OUT OF SERVICE: nothing can pass it, so a way through says nothing; the X says it all.
        if (outOfService()) return;
        Map<Side, Boolean> out = new LinkedHashMap<>();

        for (Mark mark : marks)
        {
            Direction direction = mark.getDirection();

            allow(out, mark.getA(), direction == Direction.BOTH || direction == Direction.TOWARD_A);
            allow(out, mark.getB(), direction == Direction.BOTH || direction == Direction.TOWARD_B);
        }

        // A STATION OPEN EVERY WAY SAYS NOTHING ON ITS OWN ROAD (a double curve's plain road is judged and drawn on its own).
        Set<Side> quiet = Collections.emptySet();
        if (hideOpenGreen && badge != null && badge.isStation())
        {
            Set<Side> road = badge.getA() == null || badge.getB() == null ? out.keySet()
                : EnumSet.of(badge.getA(), badge.getB());
            if (!road.stream().anyMatch(side -> Boolean.FALSE.equals(out.get(side)))) quiet = road;
        }
        // Full strength whatever the state: pale arrows on open track read as track gone wrong.
        for (Map.Entry<Side, Boolean> entry : out.entrySet())
        {
            int[] target = midpoint(entry.getKey(), width, height);

            if (target == null) continue;

            if (entry.getKey() == bareSide || quiet.contains(entry.getKey())) continue;

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

        // A seventh of the tile (MT-057, OB-037); the stroke floors below agree with this arm.
        double arm = Math.max(2.5, span / 7.0);

        // On the badge wherever it went (MT-124), else the track center.
        int[] on = badgeDrawnAt != null ? badgeDrawnAt : trackCentre(width, height);

        double centreX = on[0];
        double centreY = on[1];

        Path2D star = new Path2D.Double();

        // Six arms rather than four: four reads as a plus, which on a track diagram is a crossing
        for (int point = 0; point < 6; point++)
        {
            double angle = Math.PI * point / 3.0;

            star.moveTo(centreX, centreY);
            star.lineTo(centreX + Math.cos(angle) * arm, centreY + Math.sin(angle) * arm);
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

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

        // Where a line with no side to leave by stops: the track midpoint (OB-026).
        int[] centre = trackCentre(width, height);

        g.setStroke(new BasicStroke(Math.max(3f, span / TRACE_STROKE_PARTS),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Legs sharing track are drawn once; a square only one leg uses is drawn in that leg's shade.
        Map<String, Boolean> shared = new LinkedHashMap<>();

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

        Set<String> drawn = new LinkedHashSet<>();

        Set<String> rails = new java.util.HashSet<>();

        for (Trace trace : traces)
        {
            int[] a = endOf(trace.from, centre, width, height);
            int[] b = endOf(trace.to, centre, width, height);

            if (a == null || b == null) continue;

            String at = trace.from + ":" + trace.to;

            if (!drawn.add(at)) continue;

            // Where the line goes beats which way it runs: a leg to a manual-only station keeps its own color.
            g.setColor(trace.manualOnly ? TRACE_MANUAL
                : Boolean.TRUE.equals(shared.get(at)) ? TRACE
                : trace.forward ? TRACE : TRACE_RETURN);

            // The second pass along a rail is nudged by a fixed fraction (OB-216), not by list position.
            String rail = String.valueOf(trace.from).compareTo(String.valueOf(trace.to)) <= 0
                ? trace.from + ":" + trace.to : trace.to + ":" + trace.from;

            double nudge = rails.add(rail) ? 0 : span / TRACE_NUDGE_PARTS;

            double dx = b[0] - a[0];
            double dy = b[1] - a[1];
            double length = Math.sqrt(dx * dx + dy * dy);

            double px = length < 1 ? 0 : -dy / length * nudge;
            double py = length < 1 ? 0 : dx / length * nudge;

            // Edge to edge along the rail: the chord `heading()` takes its arrow directions from.
            g.drawLine((int) Math.round(a[0] + px), (int) Math.round(a[1] + py),
                (int) Math.round(b[0] + px), (int) Math.round(b[1] + py));
        }

        g.setStroke(new BasicStroke(Math.max(2f, span / 14f),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        g.setColor(TRACE_CHEVRON);

        for (Trace trace : traces)
        {
            int[] a = endOf(trace.from, centre, width, height);
            int[] b = endOf(trace.to, centre, width, height);

            if (a == null || b == null) continue;

            // Into the half the train heads for, along the segment's own chord.
            chevron(g, a, b, span);
        }
    }

    /** Where a trace ends on a side: its midpoint, or `centre` for a trace with no side to leave by. */
    private static int[] endOf(Side side, int[] centre, int width, int height)
    {
        return side == null ? centre : midpoint(side, width, height);
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
    private static void allow(Map<Side, Boolean> out, Side side, boolean allowed)
    {
        if (side == null) return;

        out.put(side, allowed || Boolean.TRUE.equals(out.get(side)));
    }

    /**
     * One direction of one route: an arrowhead pointing out of the tile at the side it leads to, or the same place
     * crossed out when trains may not go that way.  `span` is the tile's smaller dimension, which sets the arrowhead
     * size.
     */
    private static void arrow(Graphics2D g, int[] target, double[] heading, boolean allowed, int span)
    {
        Polygon shape = arrowShape(target, heading, allowed, span);

        if (shape == null) return;

        Object control = g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);

        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        try
        {
            if (allowed)
            {
                g.setColor(ONE_WAY);
                g.fillPolygon(shape);

                g.setColor(ONE_WAY_EDGE);
                g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawPolygon(shape);
            }
            else
            {
                g.setColor(Color.WHITE);
                g.fillPolygon(shape);

                g.setColor(CLOSED);
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawPolygon(shape);
            }
        }
        finally
        {
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                control == null ? RenderingHints.VALUE_STROKE_DEFAULT : control);
        }
    }

    /** The arrowhead `arrow` draws (FR-111), or null for a heading under a pixel; `target` is its tip. */
    public static Polygon arrowShape(int[] target, double[] heading, boolean allowed, int span)
    {
        double dx = heading[0];
        double dy = heading[1];
        double len = Math.sqrt(dx * dx + dy * dy);

        if (len < 1) return null;

        dx /= len;
        dy /= len;

        // Open is the filled one, same size: most squares are open.  The shut one is a pixel smaller and further back.
        double size = Math.max(4.0, span / 3.2) - (allowed ? 0 : 1);

        double gap = EDGE_GAP + (allowed ? 0 : 1);

        double tipX = target[0] - dx * gap;
        double tipY = target[1] - dy * gap;

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

        return new Polygon(xs, ys, 3);
    }

    /** Draws `arrowShape`'s arrowhead as the diagram does: green where trains may go that way, a red outline where not. */
    public static void paintArrow(Graphics2D g, int[] target, double[] heading, boolean allowed, int span)
    {
        arrow(g, target, heading, allowed, span);
    }

    /** How far short of the tile edge an arrowhead stops, so arrows on neighboring tiles stay separate marks. */
    private static final int EDGE_GAP = 1;

    /** The tested route: a broad yellow line, the color the running diagram uses to say "look here". */
    private static final Color TRACE = new Color(255, 214, 0);

    /** The tested route's line is one part in this many of its square's shorter side wide. */
    private static final float TRACE_STROKE_PARTS = 7f;

    /** The second pass over the same rail moves aside by one part in this many of the shorter side (OB-216). */
    private static final double TRACE_NUDGE_PARTS = 18.0;

    /** The way back where it shares no track with the way out: one color would turn out-and-back into a circuit. */
    private static final Color TRACE_RETURN = new Color(255, 150, 40);

    /**
     * A leg ending where autonomy never chooses (parking, or a square that turns every train): magenta, the free hue.
     */
    private static final Color TRACE_MANUAL = new Color(190, 40, 160);

    /** The chevrons on it, dark enough to read against the yellow they sit on. */
    private static final Color TRACE_CHEVRON = new Color(120, 80, 0);

    /** The arrival marks: a color of their own for a question of their own. */
    private static final Color ARRIVAL = new Color(255, 205, 0);

    /** Near-black outline: yellow on pale gray disappears; it also tells filled from hollow. */
    private static final Color ARRIVAL_EDGE = new Color(60, 45, 0);

    /** A barred arrival: the same mark drawn as an absence, an open gray chevron (OB-326). */
    private static final Color ARRIVAL_BARRED = new Color(140, 140, 140);

    /** The badge and train mark again over what is laid on top (MT-076): the running path would bury a station. */
    public void paintBadgeOverRun(Graphics2D g, int width, int height)
    {
        if (badge == null && !occupied) return;

        Object oldHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke oldStroke = g.getStroke();
        Color oldColor = g.getColor();
        Composite oldComposite = g.getComposite();

        try
        {
            ImageUtil.antialias(g);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

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

    /** The box a classic-icon mark is drawn in: 14 pixels at 30 for a station, 10 for a plain sensor. */
    static int classicBox(int tile, boolean station)
    {
        return station ? Math.max(9, Math.round(tile * 14 / 30f)) : Math.max(6, Math.round(tile * 10 / 30f));
    }

    private void paintBadge(Graphics2D g, int width, int height)
    {
        // One color wherever autonomy will not send a train (FR-103, MT-492); impassable is asked first.
        Color colour = badge.isImpassable() ? POINT_IMPASSABLE : badge.isParking() ? POINT_INACTIVE : POINT_ACTIVE;

        // One mark for every square out of service.
        if (badge.isImpassable())
        {
            paintOutOfService(g, width, height, colour);

            return;
        }

        int size = Math.round(iconShare * classicBox(Math.min(width, height), badge.isStation()));

        // A station whose track runs out is a terminus (FR-118).
        boolean deadEnd = badge.isStation() && badge.getDeadEnd() != null;

        boolean turns = deadEnd || badge.isTerminus() || badge.isReversing();
        boolean mayTurn = turns && !deadEnd && badge.isOptional();

        // A diamond is drawn larger: its corners cut it to half the area of a square of the same box.
        if (mayTurn)
        {
            size = Math.round(size * 1.35f);

            // Backed off a little: a full third made the diamond the loudest mark on the page.
            size -= badge.isStation() ? 2 : 1;
        }

        int[] on = trackCentre(width, height);

        int x = on[0] - size / 2;
        int y = on[1] - size / 2;

        // A classic station's diamond on a bend sits on its rails; `paintBadge` cuts the miters off at the square.
        boolean onTheBend = badge.isStation() && mayTurn && trackBends();

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

        // Never outside the square, outline included (MT-710).
        int margin = badge.isStation() ? 2 : 1;

        if (!onTheBend)
        {
            x = Math.max(margin, Math.min(width - size - margin, x));
            y = Math.max(margin, Math.min(height - size - margin, y));
        }

        badgeDrawnAt = new int[] {x + size / 2, y + size / 2};

        g.setStroke(new BasicStroke(badge.isStation() ? 2f : 1.5f));

        // Filled when named, hollow when not.
        Color fill = badge.isNamed() ? colour : Color.WHITE;
        Color line = badge.isNamed() ? Color.WHITE : colour;

        // The shape says what turning means, the size whether it is a station.  The contact is covered under a
        // station's mark.
        if (badge.isStation()) coverTheContact(g, width, height);

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

        if (badge.isStation() && badge.isStopOnly()) stopDot(g, x, y, size, line);
    }

    /**
     * The stop-only station's dot (MT-1017), a third of the icon's box and never under 3 pixels, in the outline's color,
     * centered on that outline as drawn.
     */
    private static void stopDot(Graphics2D g, int x, int y, int size, Color line)
    {
        double across = Math.max(3, size / 3.0);
        AffineTransform t = g.getTransform();
        Graphics2D d = (Graphics2D) g.create();

        try
        {
            ImageUtil.smooth(d);
            d.setColor(line);
            d.fill(new Ellipse2D.Double(drawnMiddle(g, x, size, t.getScaleX(), t.getTranslateX()) - across / 2,
                drawnMiddle(g, y, size, t.getScaleY(), t.getTranslateY()) - across / 2, across, across));
        }
        finally
        {
            d.dispose();
        }
    }

    /** An outline's middle on one axis: the default stroke control moves each end to the middle of its screen pixel. */
    private static double drawnMiddle(Graphics2D g, int at, int size, double scale, double shift)
    {
        if (RenderingHints.VALUE_STROKE_PURE.equals(g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL)))
        {
            return at + size / 2.0;
        }

        double ends = Math.floor(at * scale + shift) + Math.floor((at + size) * scale + shift);
        return ((ends + 1) / 2 - shift) / scale;
    }

    /** The sensor's contact covered under a plain sensor's turn mark, kept inside the square. */
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
            ImageUtil.smooth(c);
            c.translate(centre[0], centre[1]);
            c.rotate(Math.atan2(b[1] - a[1], b[0] - a[0]));

            cover(c, Math.min(width, height));
        }
        finally
        {
            c.dispose();
        }
    }

    /** The sensor's contact covered under a station's icon on straight track; drawn at the icon's center. */
    private static void cover(Graphics2D g, int tile)
    {
        int m = Math.round(tile * 0.2f + 2);

        g.setColor(Color.WHITE);
        g.fill(new Rectangle2D.Double(-m, -m, 2 * m, 2 * m));

        g.setColor(Color.BLACK);
        g.fill(new Rectangle2D.Double(-m, -tile * 4 / 30.0, 2 * m, tile * 8 / 30.0));
    }

    /**
     * A square out of service, on the diagram and in the editor: a white circle, an orange ring, an orange X, inside
     * the red arrows the sensor's art draws round its contact.  A classic station's sign is solid orange with a white
     * X.
     */
    private void paintOutOfService(Graphics2D g, int width, int height, Color colour)
    {
        int tile = Math.min(width, height);

        int[] on = trackCentre(width, height);

        double across = iconShare * (classicBox(tile, badge.isStation()) + (badge.isStation() ? 0 : Math.round(tile / 15f)));

        boolean filled = badge.isStation();

        boolean small = across < 16;

        float least = small ? 1.5f : 2f;

        float ring = Math.max(least, (float) across / 9f);

        double r = (across - ring) / 2.0, arm = filled ? across / 4.0 : r * (small ? 0.62 : 0.55);

        badgeDrawnAt = new int[] {on[0], on[1]};

        Graphics2D c = (Graphics2D) g.create();

        try
        {
            ImageUtil.smooth(c);
            c.translate(on[0], on[1]);

            c.setColor(filled ? colour : Color.WHITE);
            c.fill(new Ellipse2D.Double(-across / 2.0, -across / 2.0, across, across));

            if (!filled)
            {
                c.setColor(colour);
                c.setStroke(new BasicStroke(ring));
                c.draw(new Ellipse2D.Double(-r, -r, 2 * r, 2 * r));
            }

            c.setColor(filled ? Color.WHITE : colour);
            c.setStroke(new BasicStroke(Math.max(least, (float) across / 8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            c.draw(new Line2D.Double(-arm, -arm, arm, arm));
            c.draw(new Line2D.Double(-arm, arm, arm, -arm));
        }
        finally
        {
            c.dispose();
        }
    }

    /** Whether the track turns a corner: its two sides are not opposite (N, E, S and W are declared in order). */
    private boolean trackBends()
    {
        Side[] sides = trackSides();

        if (sides[0] == null || sides[1] == null || sides[0] == sides[1]) return false;

        return Math.abs(sides[0].ordinal() - sides[1].ordinal()) != 2;
    }

    /** This tile's own two track sides: the badge's own route where it has one, otherwise the first route drawn. */
    private Side[] trackSides()
    {
        return new Side[] {
            badge != null && badge.getA() != null ? badge.getA() : marks.isEmpty() ? null : marks.get(0).getA(),
            badge != null && badge.getB() != null ? badge.getB() : marks.isEmpty() ? null : marks.get(0).getB()};
    }

    /** Where paintBadge last drew, or null; only the train mark reads it, to sit ON the badge. */
    private int[] badgeDrawnAt;

    /** The midpoint of this tile's own two track sides.  Public: the run line, badges and train must agree (OB-026). */
    public int[] trackCentre(int width, int height)
    {
        Side[] sides = trackSides();

        if (sides[0] == null || sides[1] == null) return new int[] {width / 2, height / 2};

        int[] a = midpoint(sides[0], width, height);
        int[] b = midpoint(sides[1], width, height);

        if (a == null || b == null) return new int[] {width / 2, height / 2};

        return new int[] {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2};
    }

    /** The middle of the road a train stands on, named by the sides it is known to end at (MT-642). */
    public int[] trackCentreOf(List<Side> sides, int width, int height)
    {
        if (sides == null || sides.isEmpty()) return trackCentre(width, height);

        Set<Set<Side>> roads = new LinkedHashSet<>();

        if (badge != null && badge.getA() != null && badge.getB() != null) roads.add(roadOf(badge.getA(), badge.getB()));

        for (Mark mark : marks)
        {
            if (mark.getA() != null && mark.getB() != null) roads.add(roadOf(mark.getA(), mark.getB()));
        }

        int[] centre = centreOf(roads, sides, width, height);

        return centre != null ? centre : trackCentre(width, height);
    }

    /** The same from a square's own ports, for a curve or double curve; null elsewhere. */
    public static int[] trackCentreOf(List<TilePorts.Route> routes, List<Side> sides, int width, int height)
    {
        if (routes == null || sides == null || sides.isEmpty()) return null;

        Set<Set<Side>> roads = new LinkedHashSet<>();

        for (TilePorts.Route route : routes)
        {
            if (route.getA() != null && route.getB() != null) roads.add(roadOf(route.getA(), route.getB()));
        }

        return centreOf(roads, sides, width, height);
    }

    /** The middle of the one road that ends at every one of the sides, where every road is a curve, or null. */
    private static int[] centreOf(Set<Set<Side>> roads, List<Side> sides, int width, int height)
    {
        Set<Side> found = null;

        for (Set<Side> road : roads)
        {
            // CURVES ONLY: two neighboring sides, on every road of the tile
            if (road.size() != 2) return null;

            Iterator<Side> ends = road.iterator();

            if (Math.abs(ends.next().ordinal() - ends.next().ordinal()) == 2) return null;

            if (!road.containsAll(sides)) continue;

            // more than one road through these sides: not this method's to choose
            if (found != null) return null;

            found = road;
        }

        if (found == null) return null;

        Iterator<Side> ends = found.iterator();

        int[] a = midpoint(ends.next(), width, height);
        int[] b = midpoint(ends.next(), width, height);

        if (a == null || b == null) return null;

        return new int[] {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2};
    }

    /** A road as its two ends, whichever way round they were given. */
    private static Set<Side> roadOf(Side a, Side b)
    {
        return EnumSet.of(a, b);
    }

    /** Corners at the middles of the box's sides, halves included, so an odd size is no lopsided (MT-1040). */
    private void diamond(Graphics2D g, int x, int y, int size, Color fill, Color line)
    {
        Path2D.Double shape = new Path2D.Double();
        shape.moveTo(x + size / 2.0, y);
        shape.lineTo(x + size, y + size / 2.0);
        shape.lineTo(x + size / 2.0, y + size);
        shape.lineTo(x, y + size / 2.0);
        shape.closePath();
        g.setColor(fill);
        g.fill(shape);
        g.setColor(line);
        g.draw(shape);
    }

    private void paintLength(Graphics2D g, int width, int height)
    {
        String text = String.valueOf(length);

        // A third of the tile, as LayoutGrid sizes an address label (OB-019).
        int size = Math.max(8, Math.min(width, height) / 3);

        g.setFont(g.getFont().deriveFont(Font.BOLD, (float) size));

        java.awt.FontMetrics metrics = g.getFontMetrics();

        int textWidth = metrics.stringWidth(text);

        // Top right, away from the address label: same color on purpose, so they must not overlap.
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

    /** Where a side meets the tile's edge.  Public: the run line, tested path and train must agree (MT-309). */
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

        return length == other.length && selected == other.selected
            && (badge == null ? other.badge == null : badge.equals(other.badge))
            && ignored == other.ignored && stationless == other.stationless && curved == other.curved
            && portal == other.portal
            && traces.equals(other.traces) && blockedOnly == other.blockedOnly
            && occupied == other.occupied && unmeasured == other.unmeasured
            && toolMark == other.toolMark && gray == other.gray
            && allowedToo == other.allowedToo && hideOpenGreen == other.hideOpenGreen && allowedOnly == other.allowedOnly && bareSide == other.bareSide
            && marks.equals(other.marks) && arrivals.equals(other.arrivals) && empty == other.empty;
    }

    @Override
    public int hashCode()
    {
        return marks.hashCode() * 31 + length * 2
            + (selected ? 1 : 0) + (badge == null ? 0 : badge.hashCode() * 4)
            + (ignored ? 16 : 0) + (curved ? 64 : 0) + (portal ? 256 : 0) + (occupied ? 1024 : 0)
            + traces.hashCode() * 3
            + (blockedOnly ? 512 : 0) + (unmeasured ? 4096 : 0) + (allowedToo ? 8192 : 0) + (hideOpenGreen ? 524288 : 0) + (allowedOnly ? 16384 : 0)
            + (bareSide == null ? 0 : (bareSide.ordinal() + 1) * 32768) + (stationless ? 131072 : 0)
            + arrivals.hashCode() * 7 + (toolMark == null ? 0 : (toolMark.ordinal() + 1) * 262144) + (gray ? 8 : 0)
            + (empty ? 32 : 0);
    }

    @Override
    public String toString()
    {
        return marks + (length >= 0 ? " len=" + length : "") + (selected ? " selected" : "")
            + (badge == null ? "" : " " + badge)
            + (arrivals.isEmpty() ? "" : " " + arrivals)
            + (ignored ? " ignored" : "") + (empty ? " empty" : "") + (stationless ? " no station" : "")
            + (hideOpenGreen ? " no open green" : "")
            + (toolMark == null ? "" : " " + toolMark);
    }
}
