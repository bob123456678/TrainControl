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

    /**
     * The same red the diagram writes accessory addresses in.
     *
     * It was a muted purple, which is a perfectly good colour and nearly invisible at the size these
     * are drawn - a two-digit number a quarter of a tile high, over track art, in a colour with no
     * contrast against either.  Addresses solved the same problem years ago and the answer is already
     * on the screen; using a second one would only mean the reader learning two.
     */
    private static final Color LENGTH = Color.RED;


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
     * The SHAPE says what turning means here; the SIZE says whether it is a station.  Blue means
     * autonomy uses it, orange means it does not.
     *
     *                       trains do not turn    trains MAY turn    trains ALWAYS turn
     *     a station              big circle          big diamond         big square
     *     a passing point       small circle        small diamond       small square
     *
     * This used to describe the vocabulary that grid replaced - "a station is a circle, a terminus a
     * square, a reversing point a smaller square, and a plain point a small diamond" - which had grown
     * up the other way, four shapes with no system behind them.  Two of those four claims are now the
     * wrong way round: a plain point is a small CIRCLE, and a diamond means trains MAY turn, so a
     * reader interpreting a screenshot by the old legend would read a may-turn station as a plain
     * point (TD-15).  The justification is gone too: the graph window it followed was deleted.
     *
     * The five kinds the user thinks in are two questions over a station - must a train leave the way
     * it came (terminus), and does autonomy choose it on its own (parking) - which is why these are
     * independent flags rather than one enum.
     */
    public static class Badge
    {
        private final boolean station;
        private final boolean terminus;
        private final boolean optional;
        private final boolean reversing;
        private final boolean parking;
        private final boolean named;

        /**
         * Whether this square is switched OFF, as against merely left out of autonomy's choices.
         *
         * Kept apart from `parking` because they are different facts that happened to share a colour.
         * A square autonomy will not CHOOSE is still one trains pass through and stop at; a square
         * that is switched off is neither. Nothing about the shape grid says which, so a square
         * nothing can use gets a mark of its own (OB-167).
         */
        private final boolean shut;

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
            this(station, terminus, reversing, parking, named, a, b, false);
        }

        /**
         * @param optional whether turning round here is a choice rather than compulsory - a station
         *        a train MAY turn at, as against one where every arrival does
         */
        public Badge(boolean station, boolean terminus, boolean reversing, boolean parking,
            boolean named, Side a, Side b, boolean optional)
        {
            this(station, terminus, reversing, parking, named, a, b, optional, false);
        }

        /**
         * @param shut whether the square is switched off, so that nothing may pass or stop there
         */
        public Badge(boolean station, boolean terminus, boolean reversing, boolean parking,
            boolean named, Side a, Side b, boolean optional, boolean shut)
        {
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

        public boolean isStation()
        {
            return station;
        }

        public boolean isTerminus()
        {
            return terminus;
        }

        /**
         * Whether turning round here is a choice rather than compulsory.
         */
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

        /**
         * Whether the square is switched off.
         */
        public boolean isShut()
        {
            return shut;
        }

        /**
         * Whether nothing can use this square at all (OB-167).
         *
         * Switched off AND not a station: nothing stops there because it is not a place, and nothing
         * goes through because it is off. A switched-off STATION is still a place - somebody turned it
         * off and can turn it back on - so it keeps the station mark and only loses its colour.
         */
        public boolean isImpassable()
        {
            return shut && !station;
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
                && a == other.a && b == other.b;
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
     * Which sides of a station a train may arrive by, where that has been restricted.
     *
     * A different question from the direction arrows, and deliberately drawn differently.  The arrows
     * say which way traffic may FLOW through a square; this says which way a train may come in and
     * STOP.  A station can be perfectly reachable from both ends and still be one you only ever want
     * trains pulling into from the north.
     *
     * Empty on almost every square, because the default is that a train may arrive from anywhere - so
     * the diagram stays quiet and a restriction is a thing you can see.
     */
    private final List<Arrival> arrivals;

    /**
     * One side of a station, and whether trains may arrive by it.
     */
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
        this(marks, length, selected, badge, ignored, curved, portal, traces, blockedOnly, null);
    }

    /**
     * @param arrivals which sides of this station trains may arrive by, or null/empty when it takes
     *        them from anywhere - which is the usual case and draws nothing
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
     * Whether this is being drawn in the autonomy EDITOR rather than on a diagram.
     *
     * One thing turns on it: where a badge sits on a square whose track bends.  In the editor the
     * badge moves out to the corner, because there it shares the tile with two direction arrows and
     * an arrival chevron and the four of them were fighting over the same few pixels.  Everywhere
     * else - the main window, a popup, an exported image - none of that is drawn, so the badge goes
     * back onto the track it is about, which is where it belongs when it has the room.
     *
     * Set rather than passed: the constructor already takes ten arguments and this is a property of
     * the SURFACE doing the drawing rather than of the square being drawn.
     */
    public TileAnnotation inTheEditor()
    {
        this.editing = true;

        return this;
    }

    private boolean editing = false;

    /**
     * Says a train is set up to be standing on this square.
     *
     * A builder rather than another constructor argument: there are five constructors already, all
     * chaining into the longest, and a sixth position for a boolean is a thing to get wrong at a call
     * site rather than a thing to read.
     *
     * @return this
     */
    public TileAnnotation withTrain()
    {
        this.occupied = true;

        return this;
    }

    private boolean occupied = false;

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
    /**
     * Whether there is nothing here worth drawing.
     *
     * paint() opens with this, so anything missing from the list below is a thing that never gets
     * painted when it is the ONLY thing on a square.
     *
     * `occupied` was missing, which is OB-007: the train mark had been written and drawn for a while,
     * and was invisible on exactly the squares the request was about - the ones with nothing else to
     * say. A station carrying a badge was never blank, so the star appeared there and the gap looked
     * like it did not exist.
     *
     * The field had been added to equals and to hashCode. It is the method that decides whether the
     * object is worth drawing at all that tends to be missed, because it is not one anybody is looking
     * at while adding a field.
     *
     * @return whether this annotation would draw nothing
     */
    public boolean isBlank()
    {
        // `editing` is deliberately NOT here, unlike in equals and hashCode.  On its own it paints
        // nothing - it only says WHERE a badge goes, and with no badge there is nothing to place - so
        // an annotation carrying it and nothing else is still blank.  Said out loud because the comment
        // above is about a field being left out of this method, and the next reader should not have to
        // work out whether this is the same mistake again.
        return marks.isEmpty() && length < 0 && !selected && badge == null && !ignored
            && traces.isEmpty() && arrivals.isEmpty() && !occupied;
    }

    public List<Trace> getTraces()
    {
        return Collections.unmodifiableList(traces);
    }

    public List<Arrival> getArrivals()
    {
        return Collections.unmodifiableList(arrivals);
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

                // Except the route being tested, which is drawn on top of the greying.
                //
                // "Ignored" means there is nothing here for the user to SET - a route button carries no
                // track meaning of its own, so offering a length or a direction on it would invite a
                // click that does nothing.  It never meant "no route runs over this square", and a
                // route button threaded through a running line is exactly a square a route runs over:
                // this layout has forty-three of them.
                //
                // Suppressing the line here broke it into pieces wherever it crossed one, and only in
                // the editor - the running diagram draws the same route through a different painter
                // that has no notion of ignored, which is why one worked and the other did not.  A
                // drawn route is not something the user is being invited to change; it is an answer
                // being shown to a question they asked.
                if (!traces.isEmpty())
                {
                    g.setComposite(java.awt.AlphaComposite.getInstance(
                        java.awt.AlphaComposite.SRC_OVER, 1f));

                    paintTraces(g, width, height);
                }

                return;
            }

            // Knock the tile art back before drawing on it.  Thin lines over a busy icon are the same
            // contrast problem as writing on a photograph; this is the caption box behind the writing.
            // No wash when only the shut directions are drawn.  It exists to lift thin arrows off busy
            // tile art, and with the open ones gone there is little left to lift - so all it did was
            // grey most of the layout to make a handful of red arrows very slightly crisper.
            // Never on a paired link, which is the one tile where this wash says the opposite of what
            // it means.
            //
            // Grey on this diagram means "autonomy takes no notice of this square".  A link that is
            // paired and in use carries arrows, so it got the wash; a link switched off carries none,
            // so it did not - and the result was that the link being USED looked faded and the one
            // being ignored looked solid.  Exactly the wrong way round, and on the one tile type whose
            // whole job is to be either connected or not.
            //
            // Nothing is lost by skipping it.  The wash exists to lift thin arrows off busy tile art,
            // and a link's art is a single bold arrow in a box with room around it.
            if (!marks.isEmpty() && !blockedOnly && !portal)
            {
                java.awt.Composite before = g.getComposite();

                g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, DIM));
                g.setColor(DIM_COLOUR);
                g.fillRect(0, 0, width, height);

                g.setComposite(before);
            }

            paintArrows(g, width, height);

            paintArrivals(g, width, height);

            // The badge over the arrows - which is where it started, and where it can go back now that
            // a badge on a bend has moved off into the corner.  It was put underneath because the two
            // were landing on the same few pixels; they no longer do, and a badge drawn last keeps a
            // clean outline instead of having an arrowhead laid across it.
            if (badge != null) paintBadge(g, width, height);

            // And the train mark over the badge (MT-099).
            //
            // It was drawn before, and on a STATION that made it invisible: both are centred on the
            // tile, and a station's badge is half the tile across while the star's arms are a sixth -
            // so the badge covered it completely. Adam: "I can't see it - but it should overlay on top
            // of the middle of the sensor."
            //
            // On top is also the right reading. The badge says what the square IS, which does not
            // change; the star says a train is standing on it now, which does. The changing fact
            // belongs on top of the fixed one, and the star is small enough and outlined darkly
            // enough not to hide what it sits on.
            if (occupied) paintTrainMark(g, width, height);

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
     * Where a train may pull in, and where it may not.
     *
     * A chevron at the edge pointing INTO the square, which is the gesture the thing itself makes: a
     * train coming in from that side.  Deliberately unlike the direction arrows - they sit further in,
     * point outward, and are red or green - because the two say different things about the same square
     * and a reader has to be able to tell which they are looking at without being told.
     *
     * Small, and at the very edge, so a station with all its sides marked still shows its badge, its
     * name and whatever is standing on it.  A barred side is the same chevron hollowed out and struck
     * through: the shape says "arrival", the state says whether it is allowed, so there is one thing to
     * learn rather than two.
     */
    private void paintArrivals(Graphics2D g, int width, int height)
    {
        if (arrivals.isEmpty()) return;

        int span = Math.min(width, height);

        // Half the arrowhead's width, and how far in from the edge its point reaches
        double wing = Math.max(2.5, span / 7.0);
        double depth = Math.max(3.0, span / 5.0);

        // Pushed along the edge, clear of the middle.
        //
        // The direction arrows sit at the middle of each side, which is where these were too - so on a
        // station that both restricts arrivals and shows its directions, two different marks about two
        // different questions were drawn on top of each other.  Offset to one side they read as two
        // marks, and there is room: a tile edge is much wider than an arrowhead.
        double offset = span / 4.0;

        g.setStroke(new BasicStroke(Math.max(1.2f, span / 22f),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (Arrival arrival : arrivals)
        {
            int[] at = midpoint(arrival.getSide(), width, height);

            if (at == null) continue;

            // Which way is "inward" from this side
            double dx = width / 2.0 - at[0];
            double dy = height / 2.0 - at[1];
            double length = Math.sqrt(dx * dx + dy * dy);

            if (length < 1) continue;

            dx /= length;
            dy /= length;

            // Across the edge, at right angles to inward
            double px = -dy;
            double py = dx;

            // Pulled off the edge itself, which is shared with the neighbouring square, and along
            // it, clear of the direction arrow in the middle
            double baseX = at[0] + dx * 1.5 + px * offset;
            double baseY = at[1] + dy * 1.5 + py * offset;

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
                g.setColor(ARRIVAL_BARRED);
                g.draw(head);

                // Struck through, across the mouth of the chevron
                g.drawLine((int) Math.round(baseX + px * wing), (int) Math.round(baseY + py * wing),
                    (int) Math.round(tipX - px * wing * 0.2), (int) Math.round(tipY - py * wing * 0.2));

                g.drawLine((int) Math.round(baseX - px * wing), (int) Math.round(baseY - py * wing),
                    (int) Math.round(tipX + px * wing * 0.2), (int) Math.round(tipY + py * wing * 0.2));
            }
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
     * A small star in the middle of a square, saying a train is set up to stand here.
     *
     * The setup can put a train on a platform, and until now the only place that showed was the caption
     * beside it - which is on a different square, is sometimes on no square at all, and is the first
     * thing to go when somebody turns the labels off.  So the diagram could be read all the way through
     * without ever seeing where the trains had been placed.
     *
     * White with a dark edge, because the tile art underneath is not one colour: white alone vanishes on
     * a pale platform and a dark mark vanishes on the black of the rails.  Drawn last of the marks and
     * before the badge, so it sits over the arrows rather than under them.
     */
    private void paintTrainMark(Graphics2D g, int width, int height)
    {
        int span = Math.min(width, height);

        // A sixth of the tile was too much once the mark moved on top of the badge (MT-057): over a
        // station circle it read as a shape in its own right rather than as a mark ON one.
        //
        // A SEVENTH, not an eighth. An eighth made it disappear (OB-037), and the reason is the two
        // stroke widths below: they are proportional to the arm but floored at 3.0 and 1.6, and those
        // floors were chosen when the arm was span/6. At a 30px tile an eighth gives an arm of 3.75,
        // so the dark outline came out 3px wide around a 1.6px white core - the outline swallowed the
        // star and left a dark smudge.
        //
        // The floors moved with it. Three numbers that have to agree, and shrinking one of them is
        // what this comment exists to stop somebody doing again.
        double arm = Math.max(2.5, span / 7.0);

        // On the TRACK, not on the tile (MT-057).
        //
        // Adam: "slightly off center relative to the midpoint of the station. Be careful with curved
        // stations." Both halves are the same cause. The badge is centred on trackCentre - "the
        // midpoint of the route's own two sides, which is the tile centre for a straight and lands on
        // the rails for anything else" - and this was centred on the tile.
        //
        // On a straight they agree, which is why it looked only slightly off; on a bend the track
        // leaves the middle of the square and they part company completely. The star marks a train
        // standing on the RAILS, so the rails are what it belongs on - and it now sits on the badge it
        // is drawn over, whatever shape the tile is.
        // On the BADGE, wherever the badge went - not on the track independently (MT-124).
        //
        // These agreed until the editor started moving a curved station's badge to the corner, to stop
        // it fighting the two direction arrows that sit at the middles of the same two sides. The star
        // kept asking the track and drifted off the thing it is drawn on.
        //
        // Falls back to the track centre when no badge was drawn - a train standing on a plain square
        // still gets its mark, and the rails are where it belongs.
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

        // The dark edge first, as a wider stroke of the same shape underneath
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

        // Where a traced line stops when it has no side to leave by - the first and last square of a
        // run, which AutonomyEditorPanel builds with a null end deliberately.
        //
        // The same question the running overlay asks, and it was answered two different ways in the
        // same class: OB-026 gave the RUN line the track midpoint, and left this - the editor's tested
        // path, drawn on the same squares - stopping at the tile centre, which on a bend is nowhere
        // near the rails.  The fix's own javadoc claims "the run line and the badge now agree about
        // where the track is", and that was untrue for this painter (TD-4).
        int[] centre = trackCentre(width, height);

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

        int index = 0;

        for (Trace trace : traces)
        {
            int[] a = trace.from == null ? centre : midpoint(trace.from, width, height);
            int[] b = trace.to == null ? centre : midpoint(trace.to, width, height);

            if (a == null || b == null) continue;

            String at = trace.from + ":" + trace.to;

            if (!drawn.add(at)) continue;

            g.setColor(Boolean.TRUE.equals(shared.get(at)) ? TRACE
                : trace.forward ? TRACE : TRACE_RETURN);

            // A square crossed more than once - a switch a route passes through on its way out and
            // again on its way round - carries two segments that share a side.  Nudged apart so they
            // read as what they are, which is two passes, rather than as one shape.
            double nudge = drawn.size() == 1 || shared.size() < 2 ? 0 : span / 9.0 * (index - 0.5);

            double dx = b[0] - a[0];
            double dy = b[1] - a[1];
            double length = Math.sqrt(dx * dx + dy * dy);

            double px = length < 1 ? 0 : -dy / length * nudge;
            double py = length < 1 ? 0 : dx / length * nudge;

            // Edge to edge in one stroke, along the rail rather than around it.
            //
            // A curve here is not an arc and a switch's diverging leg is not a right angle: both are
            // drawn as a straight chord between the midpoints of two edges, which is what heading()
            // below has always taken its arrow directions from.  Bending the tested line through the
            // tile centre put it at forty-five degrees to the track beneath it, so the answer to "which
            // way does this route run" was drawn across the very rails it was answering about.
            g.drawLine((int) Math.round(a[0] + px), (int) Math.round(a[1] + py),
                (int) Math.round(b[0] + px), (int) Math.round(b[1] + py));

            index++;
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
            // the middle where two of them would overlap - and along the segment's own chord, so that
            // on a curve it points down the rail instead of across it.
            chevron(g, a, b, span);
        }
    }

    /**
     * A single arrowhead on the line, pointing from one point towards another.
     *
     * Shared with the running overlay, which draws the path a train is actually on in the same style as
     * the path the editor tests: same line through the square, same arrowhead on it.  Somebody who has
     * used "test a path" has already learnt to read the running diagram.
     */
    static void chevron(Graphics2D g, int[] from, int[] to, int span)
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

        // The fallback is trackCentre rather than the geometric centre, for the third time in this
        // class (TD-4).  It changes nothing on a straight - the midpoint of two opposite sides IS the
        // middle of the square - and nothing where the branch above applies.  What it corrects is a
        // bend whose side carries no route or more than one, where the arrow used to be aimed from a
        // point the track does not pass through.
        int[] from = curved && through == 1 && other != null && other != side
            ? midpoint(other, width, height) : trackCentre(width, height);

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

        // The OPEN direction is the filled one, and the two are the same size.
        //
        // It was the other way round twice over - shut arrows filled, and bigger - on the reasoning
        // that a reader scanning for what is restricted should be scanning for the boldest shapes.
        // On a real layout that is backwards: most squares are open, so most arrows were the faint
        // ones, and the diagram read as track something had gone wrong with rather than as track that
        // had been decided.  The way a line RUNS is the thing being looked at, and it should be the
        // thing that is solid.
        //
        // Filled against hollow still carries the difference without colour, so it survives being
        // printed, a poor screen, and a reader who cannot tell red from green - only now the sense is
        // the other way up.  Same size for both, so neither shouts over the other.
        // A pixel smaller where the direction is SHUT, and a pixel further back.
        //
        // Nominally the same size, the red one still read as the heavier of the two: it is drawn as
        // an outline, and an outline carries a line's worth of ink beyond the shape a filled one
        // stops at.  So the two were the same size and did not look it, and on a diagram that is
        // mostly open track the handful of red arrows were shouting.
        double size = Math.max(4.0, span / 3.2) - (allowed ? 0 : 1);

        // The tip stops short of the edge, so two arrows meeting across a tile boundary have a
        // hairline between them rather than touching and reading as one shape.  The shut one stops a
        // pixel further back again, which settles it into its own square rather than looking like
        // something arriving from the next.
        double gap = EDGE_GAP + (allowed ? 0 : 1);

        double tipX = target[0] - dx * gap;
        double tipY = target[1] - dy * gap;

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
            // Hollow, and outlined in the same weight the open ones are drawn at, so the pair are
            // plainly the same mark in two states rather than two different marks
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
     * The arrival marks: indigo, which is on this diagram neither a direction (red and green), nor a
     * tested path (yellow), nor track autonomy ignores (grey).  A colour of its own for a question of
     * its own.
     */
    private static final Color ARRIVAL = new Color(255, 205, 0);

    /**
     * Outlined in near-black rather than in a darker yellow.  Yellow on the pale grey of an unlit tile
     * is the one colour on this diagram that disappears, and the outline is what stops it - it also
     * tells the filled mark from the hollow one at a glance, which is the whole distinction.
     */
    private static final Color ARRIVAL_EDGE = new Color(60, 45, 0);

    /**
     * And a barred one, which is the same mark drawn as an absence: hollow, struck through, and closer
     * to grey than to indigo, so a shut side recedes and the open ones read as the answer.
     */
    private static final Color ARRIVAL_BARRED = new Color(150, 140, 100);

    /**
     * Draws what a sensor IS, in the graph window's own shapes and colours.
     *
     *   plain point       small diamond
     *   station           circle
     *   terminus          square, or a cross where turning round is optional
     *   reversing         small square
     *   blue              autonomy uses it
     *   orange            autonomy leaves it alone (parking, or switched off)
     *
     * Parity on purpose: the shapes and the two colours are exactly what TrainControlUI already paints
     * on the graph, so nobody has to learn a second vocabulary to read the same railway.
     *
     * Unnamed points are drawn hollow, which remains the only cue that one still needs a name.
     */
    /**
     * The badge, and the train mark on it, drawn again over whatever has been laid on top (MT-076).
     *
     * The running path is a line along the rails and is painted after this whole annotation, which is
     * deliberate - see LayoutLabel.paintComponent, where the reasoning is that a path a train is
     * actually taking matters more than the arrows saying what is permitted.
     *
     * That reasoning is about the ARROWS. It is wrong about the badges: Adam, watching a run - "the
     * intermediate stations overlap above just when reached, and then are under the green line after.
     * I like being able to see progress - keep them on top after being reached." A station is where the
     * train is going; burying it under the line to it is burying the landmark under the route.
     *
     * So the arrows stay under the line and the badge comes back over it. Called by LayoutLabel only
     * when there is an overlay to have covered it, so an ordinary diagram paints its badge once.
     *
     * @param g the tile's graphics, already translated to its own origin
     * @param width
     * @param height
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

            if (badge != null) paintBadge(g, width, height);

            // And the train mark with it, for the same reason it goes over the badge in the first
            // place - it is the most changeable fact on the square.
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

    private void paintBadge(Graphics2D g, int width, int height)
    {
        Color colour = badge.isParking() ? POINT_INACTIVE : POINT_ACTIVE;

        // A station takes a bigger badge than a passing point, as it does on the graph: 20px against
        // 17px there, the same proportion here.
        int size = Math.max(badge.isStation() ? 11 : 8,
            Math.min(width, height) / (badge.isStation() ? 2 : 3));

        // A diamond is drawn larger than a circle or a square of the same box.
        //
        // Half the area, geometrically: turn a square through forty-five degrees and the corners that
        // stick out are smaller than the ones that go in.  So a diamond nominally the same size as
        // the station circle beside it reads as the smaller mark, which is backwards - the diamonds
        // are the designations somebody is scanning for, and the circles are the ordinary case.
        if ((badge.isTerminus() || badge.isReversing()) && badge.isOptional())
        {
            size = Math.round(size * 1.35f);

            // And then a little back off each.  The third-again is about the SHAPE rather than the
            // size - a diamond covers half the box it sits in - and on both marks it overshot: a
            // station's box is already half the tile, so a third on top made it the loudest thing on
            // the page rather than an equal of the circle beside it, and even the small one came out
            // a shade heavier than the circles it sits among.
            //
            // Two off the big one and one off the small, so each ends up the weight of the mark it
            // is a variant of rather than the size the arithmetic suggested.
            size -= badge.isStation() ? 2 : 1;
        }

        // Centred on the TRACK for anything straight - the midpoint of the route's own two sides,
        // which is the tile centre for a straight and lands on the rails for anything else.
        int[] on = trackCentre(width, height);

        int x = on[0] - size / 2;
        int y = on[1] - size / 2;

        // But where the TRACK BENDS, the badge goes in the bottom left, off the rails.
        //
        // A bend's art hugs one corner, and the badge was centred on that art on the reasoning that a
        // mark belongs on the rails it is about.  True in isolation, and wrong in company: the two
        // direction arrows sit at the middles of the same two sides the chord joins, so the badge
        // landed exactly between them and three marks fought over one corner of a twenty-pixel
        // square.  A station on a curve was the hardest thing on the diagram to read.
        //
        // Asked of the ROUTE rather than of the curved flag, which is a different question wearing
        // the same word: that flag is about whether to TILT the arrows, and it is deliberately false
        // for a curve carrying a sensor - a tilted arrow disappears into the heavy feedback art.  So
        // every curved s88 - which is to say every station on a curve, the whole case this is for -
        // came through here as "not curved" and kept its badge in the middle.  Two sides that are not
        // opposite means the track turns a corner, whatever is drawn on it.
        //
        // The bottom left whichever way it bends: three of the four curves bend away from it
        // entirely, the fourth clips only its corner, and it is clear of the length, which is written
        // top right.  Being off the track costs nothing - a badge is one square's worth of mark on
        // one square, and nobody has to trace which rail it sits on to know which square it means.
        if (editing && badge.isStation() && trackBends())
        {
            // In from the very corner, at the author's eye.  Hard against the edges the badge read as
            // something that had slipped off the tile rather than as a mark placed on it, and it was
            // tight against the arrival chevron at the middle of the west side.
            //
            // STATIONS only.  A station's badge is the big one - half the tile across, and bigger
            // again where it is a diamond - which is why it collided with the arrows in the first
            // place.  A passing point's is a third of the tile and sits on the track without
            // crowding anything, so moving it out to the corner would take a mark off the rails it
            // is about in exchange for solving a problem it does not have.  It would also break the
            // one thing the badges do best: a page of small circles sitting on the track, with the
            // few that are stations standing out from it.
            x = CORNER_INSET;
            y = height - size - CORNER_INSET;
        }

        // never let it hang outside the square
        x = Math.max(1, Math.min(width - size - 1, x));
        y = Math.max(1, Math.min(height - size - 1, y));

        // Where the badge ACTUALLY went, for the star drawn on top of it (MT-124).
        //
        // Recorded rather than recomputed. The placement above is three rules deep - the track centre,
        // then the corner for a curved station in the editor, then the clamp - and a second copy of
        // that arithmetic would be a second chance to disagree. The star asked trackCentre directly, so
        // it stayed on the rails while the badge moved to the corner. Adam: "Move the * so it aligns
        // with the offset placement."
        badgeDrawnAt = new int[] {x + size / 2, y + size / 2};

        g.setStroke(new BasicStroke(badge.isStation() ? 2f : 1.5f));

        // Filled when named, hollow when not - so an unnamed point is visible but visibly unfinished.
        Color fill = badge.isNamed() ? colour : Color.WHITE;
        Color line = badge.isNamed() ? Color.WHITE : colour;

        // THE SHAPE SAYS WHAT TURNING MEANS HERE.  THE SIZE SAYS WHETHER IT IS A STATION.
        //
        //                     trains do not turn    trains MAY turn    trains ALWAYS turn
        //     a station            big circle          big diamond         big square
        //     a passing point     small circle        small diamond       small square
        //
        // Two questions, two dimensions, and every square on the diagram is one cell of that grid.
        // It had grown up the other way: a cross for a station that may turn, a square for one that
        // must, a circle for a station, a diamond for a plain point - four shapes with no system
        // behind them, so each one had to be learned separately and none of them said anything about
        // the others.  Here, having seen any two marks, the rest can be read off.
        //
        // The bordering cases are what makes it worth the trouble.  A station where every train turns
        // and a siding where every train turns are the SAME fact about a railway happening in two
        // places, and they now look like the same fact in two sizes.
        // A SQUARE NOTHING CAN USE IS A CROSS (OB-167).
        //
        // Adam: "station no + must reverse + disabled gets same large square icon as inactive
        // terminus.  if nothing can pass, the icon should be a small x."
        //
        // The grid above answers two questions and neither of them is "is this square switched off",
        // which only ever reached the colour - so a reversing point that had been turned off drew the
        // same square as one that was working, in the same orange as a parking station. Its shape was
        // saying "every train turns here" about a square no train can enter.
        //
        // A cross rather than a fourth shape in the grid, because it is not a third answer to "does
        // this square turn trains": it is the square opting out of the question. Small, and drawn as
        // strokes rather than filled, so it reads as an absence beside the marks that are present.
        if (badge.isImpassable())
        {
            int mark = Math.max(6, Math.min(width, height) / 4);

            int cx = on[0];
            int cy = on[1];

            g.setStroke(new BasicStroke(2f));
            g.setColor(colour);

            g.drawLine(cx - mark / 2, cy - mark / 2, cx + mark / 2, cy + mark / 2);
            g.drawLine(cx - mark / 2, cy + mark / 2, cx + mark / 2, cy - mark / 2);

            return;
        }

        boolean turns = badge.isTerminus() || badge.isReversing();
        boolean mayTurn = turns && badge.isOptional();

        if (turns && mayTurn)
        {
            diamond(g, x, y, size, fill, line);
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
     * How far in from the bottom left corner a badge on a bend sits, in the editor.
     *
     * One pixel put it hard against both edges; this lifts it clear without moving it back under the
     * track it was moved out of.
     */
    private static final int CORNER_INSET = 7;

    /**
     * Whether the track through this square turns a corner.
     *
     * The route's two sides, and whether they are opposite each other.  N, E, S and W are declared in
     * that order, so opposite sides are two apart either way round and everything else is a bend.
     *
     * @return true where the track joins two sides that are not opposite
     */
    private boolean trackBends()
    {
        Side sideA = badge != null && badge.getA() != null ? badge.getA()
            : marks.isEmpty() ? null : marks.get(0).getA();

        Side sideB = badge != null && badge.getB() != null ? badge.getB()
            : marks.isEmpty() ? null : marks.get(0).getB();

        if (sideA == null || sideB == null || sideA == sideB) return false;

        return Math.abs(sideA.ordinal() - sideB.ordinal()) != 2;
    }

    /**
     * Where paintBadge last drew, or null if it has not drawn on this pass.
     *
     * Only ever read by the train mark, which is painted after the badge and belongs ON it.
     */
    private int[] badgeDrawnAt;

    /**
     * The midpoint of this tile's own two track sides - the tile centre for a straight, and on the
     * rails for anything else.
     *
     * Public because the running overlay needs it too: the end of a run has one side and a null, and
     * without this it stopped in the middle of the square (OB-026).  The badges have been placed by it
     * since MT-057, so the run line and the badge now agree about where the track is.
     *
     * @param width
     * @param height
     * @return x and y within the tile
     */
    public int[] trackCentre(int width, int height)
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

        // A third of the tile, which is what LayoutGrid gives an address label (OB-019).
        //
        // It was a quarter, and the two numbers sit in opposite corners of the same square in the same
        // colour - so the smaller one read as a footnote to the larger rather than as the same kind of
        // fact about the tile.
        int size = Math.max(8, Math.min(width, height) / 3);

        g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, (float) size));

        java.awt.FontMetrics metrics = g.getFontMetrics();

        int textWidth = metrics.stringWidth(text);

        // Top right, which is the corner away from the address the diagram writes at the leading edge.
        // The two are the same colour on purpose, so they must not be able to sit on top of one another
        // and read as one number.
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
     * Where a side meets the edge of the tile.
     */
    static int[] midpoint(Side side, int width, int height)
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

        // `editing` is in here because it changes the PICTURE - it decides whether a station's badge
        // moves out to the corner on a bend - and equals is what LayoutLabel asks to decide whether a
        // redraw is needed.  Two annotations differing only in it were indistinguishable to that
        // question (TD-11).
        //
        // The two populations of labels happen to be segregated today, so no live path reached it. It
        // is left as a trap otherwise, twelve lines below a comment recording the same omission being
        // made once before.
        return length == other.length && selected == other.selected
            && (badge == null ? other.badge == null : badge.equals(other.badge))
            && ignored == other.ignored && curved == other.curved && portal == other.portal
            && traces.equals(other.traces) && blockedOnly == other.blockedOnly
            && occupied == other.occupied && editing == other.editing
            && marks.equals(other.marks) && arrivals.equals(other.arrivals);
    }

    @Override
    public int hashCode()
    {
        return marks.hashCode() * 31 + length * 2
            + (selected ? 1 : 0) + (badge == null ? 0 : badge.hashCode() * 4)
            + (ignored ? 16 : 0) + (curved ? 64 : 0) + (portal ? 256 : 0) + (occupied ? 1024 : 0)
            + traces.hashCode() * 3
            + (blockedOnly ? 512 : 0) + (editing ? 2048 : 0) + arrivals.hashCode() * 7;
    }

    @Override
    public String toString()
    {
        return marks + (length >= 0 ? " len=" + length : "") + (selected ? " selected" : "")
            + (badge == null ? "" : " " + badge)
            + (arrivals.isEmpty() ? "" : " " + arrivals)
            + (ignored ? " ignored" : "");
    }
}
