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

    /** Draws the station's icon at this share of its size, for a picture whose arrows are read first (FR-111). */
    public TileAnnotation withSmallerIcon(float share)
    {
        this.iconShare = share;
        return this;
    }

    private float iconShare = 1f;

    /**
     * Grays the square as one autonomy takes no notice of, but still draws its length and outline (OB-353); no arrows.
     */
    public TileAnnotation withNoStation()
    {
        this.stationless = true;
        return this;
    }

    private boolean stationless = false;

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

        /** Autonomy will never choose this station for the train. */
        NEVER,

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

        java.awt.Composite before = g.getComposite();

        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, MARK_FILL_ALPHA));
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
            g.draw(new java.awt.geom.Rectangle2D.Float(thick / 2, thick / 2, width - thick, height - thick));

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
            case NEVER: return TRACE_MANUAL;
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

    /** Whether the arrows follow a curve's chord. */
    public boolean isCurved()
    {
        return curved;
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
        // `editing` is deliberately NOT here, unlike in equals and hashCode: on its own it paints nothing (it only
        // says what color a badge is), so an annotation carrying it and nothing else is still blank.
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

        java.awt.Composite oldComposite = g.getComposite();

        try
        {
            ImageUtil.antialias(g);

            // A square autonomy takes no notice of is grayed out and nothing else is drawn on it; anything drawn
            // would invite a click.
            if (ignored)
            {
                paintGreyedOut(g, width, height);

                // The tested route still shows over the graying: it answers the user's question.
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

    private static java.awt.geom.Path2D head(double baseX, double baseY, double dx, double dy, double px, double py,
        double wing, double depth)
    {
        java.awt.geom.Path2D head = new java.awt.geom.Path2D.Double();

        head.moveTo(baseX + px * wing, baseY + py * wing);
        head.lineTo(baseX + dx * depth, baseY + dy * depth);
        head.lineTo(baseX - px * wing, baseY - py * wing);

        return head;
    }

    /**
     * The arrival chevron's shape for a square of this size, its base at the given point, pointing along `heading` (a
     * unit vector), for a picture that draws one off the diagram's place (FR-111).
     */
    public static java.awt.geom.Path2D arrivalShape(double baseX, double baseY, double[] heading, int span)
    {
        return head(baseX, baseY, heading[0], heading[1], -heading[1], heading[0], Math.max(2.5, span / 7.0),
            Math.max(3.0, span / 5.0));
    }

    /** Draws an arrival chevron: filled where trains may stop coming that way, a gray open chevron where not. */
    public static void paintArrival(Graphics2D g, java.awt.geom.Path2D head, boolean allowed, int span)
    {
        java.awt.Stroke was = g.getStroke();

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

        java.util.Map<Side, Boolean> out = new java.util.LinkedHashMap<>();

        for (Mark mark : marks)
        {
            Direction direction = mark.getDirection();

            allow(out, mark.getA(), direction == Direction.BOTH || direction == Direction.TOWARD_A);
            allow(out, mark.getB(), direction == Direction.BOTH || direction == Direction.TOWARD_B);
        }

        // Full strength whatever the state: pale arrows on open track read as track gone wrong.
        for (java.util.Map.Entry<Side, Boolean> entry : out.entrySet())
        {
            int[] target = midpoint(entry.getKey(), width, height);

            if (target == null) continue;

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

        // Where a line with no side to leave by stops: the track midpoint (OB-026).
        int[] centre = trackCentre(width, height);

        g.setStroke(new BasicStroke(Math.max(3f, span / TRACE_STROKE_PARTS),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Legs sharing track are drawn once; a square only one leg uses is drawn in that leg's shade.
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
            int[] a = trace.from == null ? centre : midpoint(trace.from, width, height);
            int[] b = trace.to == null ? centre : midpoint(trace.to, width, height);

            if (a == null || b == null) continue;

            // Into the half the train heads for, along the segment's own chord.
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
    private static void arrow(Graphics2D g, int[] target, double[] heading, boolean allowed, int span)
    {
        java.awt.Polygon shape = arrowShape(target, heading, allowed, span);

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
    public static java.awt.Polygon arrowShape(int[] target, double[] heading, boolean allowed, int span)
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

        return new java.awt.Polygon(xs, ys, 3);
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
        java.awt.Composite oldComposite = g.getComposite();

        try
        {
            ImageUtil.antialias(g);

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

    /** The icons stations are drawn with, one setting. */
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

        // A station takes FR-118's icons while they are the ones drawn.
        if (badge.isStation() && stationIcons() == StationIcons.SHAPES)
        {
            paintStation(g, width, height, colour);

            return;
        }

        boolean classic = stationIcons() == StationIcons.CLASSIC;

        int size = Math.round(iconShare * (classic ? classicBox(Math.min(width, height), badge.isStation())
            : Math.max(badge.isStation() ? 11 : 8, Math.min(width, height) / (badge.isStation() ? 2 : 3))));

        // A station whose track runs out is a terminus (FR-118), with the classic icons.
        boolean deadEnd = classic && badge.isStation() && badge.getDeadEnd() != null;

        boolean turns = deadEnd || badge.isTerminus() || badge.isReversing();
        boolean mayTurn = turns && !deadEnd && badge.isOptional();

        // A diamond is drawn larger: its corners cut it to half the area of a square of the same box.
        if (mayTurn)
        {
            size = Math.round(size * 1.35f);

            // Backed off a little: a full third made the diamond the loudest mark on the page.
            size -= badge.isStation() ? 2 : 1;
        }

        // A plain sensor's turn mark is bigger, over its contact, with FR-118's icons only.
        if (!classic && turns) size = Math.round(Math.min(width, height) * (mayTurn ? 0.53f : 0.4f));

        int[] on = trackCentre(width, height);

        int x = on[0] - size / 2;
        int y = on[1] - size / 2;

        // A classic station's diamond on a bend sits on its rails; `paintBadge` cuts the miters off at the square.
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

        // Never outside the square, outline included (MT-710).
        int margin = badge.isStation() ? 2 : 1;

        if (!onTheBend)
        {
            x = Math.max(margin, Math.min(width - size - margin, x));
            y = Math.max(margin, Math.min(height - size - margin, y));
        }

        badgeDrawnAt = new int[] {x + size / 2, y + size / 2};

        g.setStroke(new BasicStroke(badge.isStation() ? 2f : 1.5f));

        // Filled when named, hollow when not; white with its color round it in the editor.
        boolean white = editing && !classic;

        Color fill = badge.isNamed() && !white ? colour : Color.WHITE;
        Color line = badge.isNamed() && !white ? Color.WHITE : colour;

        // The shape says what turning means, the size whether it is a station.  The contact is covered under a
        // station's mark, as under FR-118's icons.
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

    /** How tall a station's icon is across its track (FR-118), no taller than its contact. */
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
     * A station's icon (FR-118): a rounded block; the hexagon where trains may turn; two terminus shapes back to back
     * where they must; a flat-ended terminus where its track runs out.  What falls outside spills onto the neighbors.
     */
    private void paintStation(Graphics2D g, int width, int height, Color colour)
    {
        int tile = Math.min(width, height);

        int h = Math.max(7, Math.round(tile * ICON_SHARE * iconShare));

        boolean large = tile >= LARGE_TILE;

        boolean bends = trackBends();

        int[] centre = trackCentre(width, height);

        int[] a = midpoint(badge.getA(), width, height), b = midpoint(badge.getB(), width, height);

        boolean known = a != null && b != null && badge.getA() != badge.getB();

        double angle = known ? Math.atan2(b[1] - a[1], b[0] - a[0]) : 0;

        Side deadEnd = known && (badge.getDeadEnd() == badge.getA() || badge.getDeadEnd() == badge.getB())
            ? badge.getDeadEnd() : null;

        if (deadEnd != null && deadEnd == badge.getB()) angle += Math.PI;

        // White with its color round it in the editor: the arrows over the icon are what it is read for.
        Color fill = badge.isNamed() && !editing ? colour : Color.WHITE;
        Color line = badge.isNamed() && !editing ? Color.WHITE : colour;

        boolean turns = badge.isTerminus() || badge.isReversing();

        float stroke = large ? ICON_STROKE_LARGE : ICON_STROKE_SMALL;

        int turning = Math.round(h * ICON_LENGTH_RATIO) - (large ? 2 : 1), terminus = Math.round(h * ICON_LENGTH_RATIO) - 1;

        java.awt.Shape outline = !turns && deadEnd == null ? blockShape(h)
            : deadEnd != null ? hexagon(terminus, h, true, large ? h / 2.0 : h * TERMINUS_POINT_SMALL)
            : badge.isOptional() ? hexagon(turning, h, false, h / 2.0)
            : mustTurnShape(h, turning, large);

        java.awt.Shape stroked = new BasicStroke(stroke).createStrokedShape(outline);

        java.awt.geom.Rectangle2D needs =
            java.awt.geom.AffineTransform.getRotateInstance(angle).createTransformedShape(stroked).getBounds2D();

        double scale = 1;

        // On a bend, full size on its rails and spilling onto the squares beside it (MT-710).
        boolean spills = known && bends;

        // Elsewhere moved toward the middle until wholly inside, made smaller only if still too big.
        double[] at = spills ? new double[] {centre[0], centre[1], scale}
            : fitInside(needs, scale, centre[0], centre[1], width, height);

        scale = at[2];

        badgeDrawnAt = new int[] {(int) Math.round(at[0]), (int) Math.round(at[1])};

        // What it covers, for the diagram's redraw of what lies over a spill; built once per size (a stall otherwise).
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

        // The contact covered on a bend too, once (not again with the spill).
        if (spills && !spilling)
        {
            Graphics2D c = (Graphics2D) g.create();

            try
            {
                c.clipRect(0, 0, width, height);
                ImageUtil.smooth(c);
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
            // A spill is cut at the square's edges so no pixel is drawn twice.
            if (spills) s.clip(spilling ? outside(width, height) : new java.awt.Rectangle(0, 0, width, height));

            // Pure, as the page drew them: stroke control would let the must-turn's outlines meet across the gap.
            ImageUtil.smooth(s);
            s.translate(at[0], at[1]);
            s.rotate(angle);

            if (known && !bends) cover(s, tile);

            s.scale(scale, scale);
            s.setStroke(new BasicStroke(stroke));

            if (deadEnd != null)
            {
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

    /** Whether the station icon reaches past the square: only FR-118's icons, on a curve on the diagram. */
    public boolean spillsOverItsSquare()
    {
        // Not a square the editor shades; classic marks stay inside their square.
        return stationIcons() == StationIcons.SHAPES && badge != null && badge.isStation() && !badge.isImpassable() && !ignored && badge.getA() != null
            && badge.getB() != null && badge.getA() != badge.getB() && trackBends();
    }

    /**
     * What of the icon falls outside the square, drawn over its neighbors; the graphics are at this square's origin.
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

    /** The spill's cover, in this square's coordinates, or null; the diagram redraws what lies over it. */
    public java.awt.Shape spillShape(int width, int height)
    {
        int[] built = spillFor;

        if (built == null || built[0] != width || built[1] != height || !spillsOverItsSquare()) return null;

        return spillRegion;
    }

    /** This square's arrows and barred arrivals again over a neighbor's spilled station; clipped to the square. */
    public void paintArrowsAgain(Graphics2D g, int width, int height)
    {
        if (ignored) return;

        Graphics2D a = (Graphics2D) g.create();

        try
        {
            ImageUtil.antialias(a);
            a.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1f));

            paintArrows(a, width, height);

            paintArrivals(a, width, height);

            // AND ITS OUTLINES, which its own paint draws over everything on it: the spill broke them
            if (toolMark == ToolMark.CLICKABLE) paintToolBorder(a, width, height);

            paintOutlines(a, width, height);
        }
        finally
        {
            a.dispose();
        }
    }

    /** The sensor's contact covered under a station's icon on straight track (FR-118); drawn at the icon's center. */
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
     * Where an icon goes to be wholly inside its square (MT-710): the asked place, else the first fit, else smaller.
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

    /** Two terminus shapes back to back across a 4-pixel gap the track shows through, `total` long (MT-710). */
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
     * A square out of service, on the diagram and in the editor: a white circle, an orange ring, an orange X, inside
     * the red arrows the sensor's art draws round its contact.  A classic station's sign is solid orange with a white
     * X.
     */
    private void paintOutOfService(Graphics2D g, int width, int height, Color colour)
    {
        int tile = Math.min(width, height);

        int[] on = trackCentre(width, height);

        boolean shapes = stationIcons() == StationIcons.SHAPES;

        double across = iconShare * (shapes ? Math.round(tile * 0.47f)
            : classicBox(tile, badge.isStation()) + (badge.isStation() ? 0 : Math.round(tile / 15f)));

        boolean filled = !shapes && badge.isStation();

        boolean small = !shapes && across < 16;

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

    /** The midpoint of this tile's own two track sides.  Public: the run line, badges and train must agree (OB-026). */
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

    /** The middle of the road a train stands on, named by the sides it is known to end at (MT-642). */
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

    /** The same from a square's own ports, for a curve or double curve; null elsewhere. */
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

        // A third of the tile, as LayoutGrid sizes an address label (OB-019).
        int size = Math.max(8, Math.min(width, height) / 3);

        g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, (float) size));

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

        // `editing` is in here because it changes the PICTURE (whether a named point's icon is filled with its color or
        // white) and equals is what LayoutLabel asks to decide whether a redraw is needed.
        return length == other.length && selected == other.selected
            && (badge == null ? other.badge == null : badge.equals(other.badge))
            && ignored == other.ignored && stationless == other.stationless && curved == other.curved
            && portal == other.portal
            && traces.equals(other.traces) && blockedOnly == other.blockedOnly
            && occupied == other.occupied && editing == other.editing && unmeasured == other.unmeasured
            && toolMark == other.toolMark && gray == other.gray
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
            + arrivals.hashCode() * 7 + (toolMark == null ? 0 : (toolMark.ordinal() + 1) * 262144) + (gray ? 8 : 0);
    }

    @Override
    public String toString()
    {
        return marks + (length >= 0 ? " len=" + length : "") + (selected ? " selected" : "")
            + (badge == null ? "" : " " + badge)
            + (arrivals.isEmpty() ? "" : " " + arrivals)
            + (ignored ? " ignored" : "") + (stationless ? " no station" : "")
            + (toolMark == null ? "" : " " + toolMark);
    }
}
