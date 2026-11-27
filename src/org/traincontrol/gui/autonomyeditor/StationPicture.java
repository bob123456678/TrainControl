package org.traincontrol.gui.autonomyeditor;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.traincontrol.automation.StandingRules;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.InfoMark;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.OnMouse;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.widgets.HandPointer;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.util.I18n;

/**
 * The station and the track squares its track meets, as the track diagram draws them at 60 pixels (FR-111): the station
 * with the diagram's arrows, and its ways in to stop, from `Layout.standingRulesAt`; the next squares with the arrows in
 * effect on them.  A click on a mark goes to the window, which presses the control that owns it.  Holds no rule.
 */
final class StationPicture extends JPanel
{
    /** The track diagram's 60 pixel square. */
    static final int SQUARE = 60;

    /**
     * The station's icon is drawn smaller on a straight station, so the marks beside it are what the picture is read
     * for, and whole on a curve, so the station's type still shows.
     */
    private static final float ICON = 0.85f;

    /** The ways in are drawn smaller than the diagram's, so that a way in and a way out fit on one side. */
    private static final int CHEVRON = 52;

    /** How far a way in on a straight side stands off the axis, clear of the station's art and of its way out. */
    private static final double ALONGSIDE = 22;

    /** Where a way in that stands by its edge starts: inward from the edge, and along it beside the way out. */
    private static final double EDGE = 4;

    private static final double BESIDE = 16;

    /** What a click on a mark does, and what its tooltip says: the window's, as the window owns the controls. */
    interface Doors
    {
        /** @return what a click on this mark will do, or why it won't */
        String tip(Mark mark);

        void press(Mark mark);
    }

    /** One clickable mark: a station's way in at a side, a next square's arrow at one of its sides, or the station. */
    static final class Mark
    {
        enum Kind
        {
            IN, NEIGHBOR, DEPART
        }

        final Kind kind;
        final TileKey square;
        final Side side;
        final boolean open;
        final Shape shape;

        Mark(Kind kind, TileKey square, Side side, boolean open, Shape shape)
        {
            this.kind = kind;
            this.square = square;
            this.side = side;
            this.open = open;
            this.shape = shape;
        }

        /** On the shape itself, or `near` it: within a few pixels of its outline. */
        boolean hit(Point at, boolean near)
        {
            return near ? NEAR.createStrokedShape(shape).contains(at) : shape.contains(at);
        }

        double size()
        {
            Rectangle area = shape.getBounds();

            return (double) area.width * area.height;
        }

        /** How far the point is from the middle of the mark's corners. */
        double apart(Point at)
        {
            double x = 0;
            double y = 0;
            int corners = 0;

            double[] each = new double[6];

            for (PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next())
            {
                if (it.currentSegment(each) == PathIterator.SEG_CLOSE) continue;

                x += each[0];
                y += each[1];
                corners++;
            }

            return corners == 0 ? Double.MAX_VALUE : at.distance(x / corners, y / corners);
        }
    }

    /** How near a mark's outline a click still presses it, where it is on no mark. */
    private static final BasicStroke NEAR = new BasicStroke(6);

    private final Block block = new Block();

    /** Holds the squares' art, painted by `Block`; the mouse never reaches it, so no tooltip or click lands on the art. */
    private final JPanel art = new JPanel(null)
    {
        @Override
        public boolean contains(int x, int y)
        {
            return false;
        }
    };

    private transient AutonomySession session;
    private transient TileKey showing;

    /** The most rows any s88 square of the setup draws, so the window can keep room for that box (`spareHeight`). */
    private int tallest = 1;
    private transient LayoutLabel square;
    private transient TileAnnotation drawn;
    private transient StandingRules rules;
    private transient Doors doors;

    private final transient Map<Side, LayoutLabel> next = new EnumMap<>(Side.class);
    private final transient Map<Side, TileAnnotation> nextMarks = new EnumMap<>(Side.class);

    StationPicture()
    {
        super(new BorderLayout(0, 6));

        setName("station.picture");

        JPanel heading = new JPanel();

        heading.setLayout(new BoxLayout(heading, BoxLayout.X_AXIS));
        heading.setOpaque(false);
        heading.add(UiStyle.heading(I18n.t("autosetup.ui.stationPictureHeading")));
        heading.add(Box.createHorizontalStrut(4));

        JLabel mark = InfoMark.label(I18n.t("autosetup.ui.stationPictureHelp"));

        mark.setFocusable(true);

        heading.add(mark);

        add(heading, BorderLayout.NORTH);

        block.setName("station.picture.ways");
        block.setBackground(Color.WHITE);
        block.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(204, 204, 204)),
            BorderFactory.createEmptyBorder(10, 8, 10, 8)));

        art.setOpaque(false);
        block.add(art);

        add(block, BorderLayout.CENTER);
    }

    /** Puts the squares up when the station changes, and what the setup says on them every time. */
    void show(AutonomySession session, TileKey tile, TrainControlUI main, Doors doors)
    {
        this.session = session;
        this.doors = doors;

        if (!tile.equals(showing))
        {
            art.removeAll();
            next.clear();

            rules = null;

            square = label(tile, main);

            if (square != null)
            {
                for (Side side : Side.values())
                {
                    LayoutLabel beside = connects(tile, side) ? label(tile.beside(side), main) : null;

                    if (beside != null) next.put(side, beside);
                }
            }

            showing = tile;

            block.revalidate();
        }

        int most = 1;

        for (TileKey each : session.getReducer() == null ? Collections.<TileKey>emptySet()
            : session.getReducer().getPoints().keySet())
        {
            most = Math.max(most, 1 + (connects(each, Side.N) ? 1 : 0) + (connects(each, Side.S) ? 1 : 0));
        }

        if (most != tallest) block.revalidate();

        tallest = most;

        setVisible(square != null);

        if (square == null) return;

        drawStation();

        nextMarks.clear();

        for (Side side : next.keySet())
        {
            TileKey beside = tile.beside(side);

            // Every road's arrows in effect, green too whatever the diagram's settings: they are the picture's controls (MT-1061)
            List<TileAnnotation.Mark> kept = new ArrayList<>();

            // A run pointing opposite ways is closed, as its first square draws it (OB-361)
            boolean shut = session.getGraph().runsPointingOppositeWays().values().stream()
                .anyMatch(run -> run.getTiles().contains(beside));

            for (TileAnnotation.Mark road : session.directionMarks(beside))
            {
                kept.add(shut ? new TileAnnotation.Mark(road.getA(), road.getB(), Direction.NONE) : road);
            }

            if (!kept.isEmpty() && !session.isDisabledLink(beside))
            {
                nextMarks.put(side, new TileAnnotation(kept, -1, false, null, false, session.isCurveSquare(beside), false, null,
                    true).withAllowedDirections());
            }
        }

        block.repaint();
    }

    /** The station's square with every arrow, whatever the diagram's settings (MT-1106); the ways in are ours. */
    private void drawStation()
    {
        TileAnnotation diagram = session.staticAnnotations(Collections.singleton(showing), true, true).get(showing);
        drawn = diagram == null ? null : diagram.withoutArrivals().withSmallerIcon(diagram.isCurved() ? 1f : ICON);
        square.setAutonomyAnnotation(drawn);
    }

    /** Draws the station's square again, whose directions may have changed meanwhile. */
    void refresh()
    {
        if (square == null || showing == null || session == null) return;
        drawStation();
        block.repaint();
    }

    /** The railway's answer about the station, or null where there is none: the station then wears no ways. */
    void showWays(StandingRules rules)
    {
        this.rules = rules;

        block.repaint();
    }

    /** Whether the next square's track meets this side of the station. */
    private boolean connects(TileKey tile, Side side)
    {
        TileKey beside = tile.beside(side);

        if (session.getGraph() == null || !session.getGraph().getTiles().containsKey(beside)) return false;

        for (TilePorts.Route road : session.getRoutes(tile).values())
        {
            if (road.getA() != side && road.getB() != side) continue;

            for (TilePorts.Route theirs : session.getRoutes(beside).values())
            {
                if (theirs.getA() == side.opposite() || theirs.getB() == side.opposite()) return true;
            }
        }

        return false;
    }

    /** A square of the diagram, as a picture, a sensor as free (OB-485): the pointer over it is the picture's. */
    private LayoutLabel label(TileKey at, TrainControlUI main)
    {
        LayoutDiagramComponent art = session.getGraph() == null ? null : session.getGraph().getTiles().get(at);

        if (art == null || main == null) return null;

        LayoutLabel out = LayoutLabel.sensorsAsFree(art, block, SQUARE, main);

        this.art.add(out);

        return out;
    }

    /**
     * Where a square sits: the station in the middle of a 3 by 3 block, a next square beside it; the row above or below
     * is left out where no square is drawn there, and the rows drawn are centered in the frame (MT-961).
     */
    private Rectangle cell(Side side)
    {
        Insets inside = block.getInsets();

        int left = inside.left + Math.max(0, (block.getWidth() - inside.left - inside.right - 3 * SQUARE) / 2);
        int top = inside.top + Math.max(0, (block.getHeight() - inside.top - inside.bottom - rows() * SQUARE) / 2);

        int x = side == Side.W ? 0 : side == Side.E ? 2 : 1;
        int y = (side == Side.N ? 0 : side == Side.S ? 2 : 1) - (next.containsKey(Side.N) ? 0 : 1);

        return new Rectangle(left + x * SQUARE, top + y * SQUARE, SQUARE, SQUARE);
    }

    /** How much shorter this box is than the tallest the setup can need: room the window keeps, so no re-aim moves it. */
    int spareHeight()
    {
        return Math.max(0, tallest - rows()) * SQUARE;
    }

    /** The rows drawn: the station's, and those of a next square above or below it. */
    private int rows()
    {
        return 1 + (next.containsKey(Side.N) ? 1 : 0) + (next.containsKey(Side.S) ? 1 : 0);
    }

    /** Every mark, where it is drawn and clicked; one list for painting, clicks, tooltips and tests. */
    List<Mark> marks()
    {
        List<Mark> out = new ArrayList<>();

        if (square == null) return out;

        Rectangle middle = cell(null);

        for (Side side : next.keySet())
        {
            if (rules != null) wayIn(out, middle, side);

            TileAnnotation their = nextMarks.get(side);

            if (their != null) neighborMarks(out, cell(side), showing.beside(side), their);
        }

        // A click on the station steps May depart (OB-511), around its ways in, so marks never overlap
        Area station = new Area(middle);
        for (Mark mark : out) if (mark.kind == Mark.Kind.IN) station.subtract(new Area(mark.shape));
        out.add(new Mark(Mark.Kind.DEPART, showing, null, true, station));
        return out;
    }

    /** A station side's way in, beside the side's axis by its edge, where the track diagram draws one (OB-494). */
    private void wayIn(List<Mark> out, Rectangle at, Side side)
    {
        Side other = otherEnd(side);

        boolean bent = other != null && other.opposite() != side && other != side;

        // None on a square that is no station (OB-441)
        boolean wayIn = false;

        for (TileAnnotation.Arrival arrival : session.arrivalMarks(showing, false)) wayIn |= arrival.getSide() == side;

        if (!wayIn) return;

        out.add(new Mark(Mark.Kind.IN, showing, side, rules.waysIn().contains(side.name()),
            apartChevron(at, side, other, bent)));
    }

    /** The other end of the station's road that meets this side, or null. */
    private Side otherEnd(Side side)
    {
        Side other = null;

        for (TilePorts.Route road : session.getRoutes(showing).values())
        {
            if (road.getA() == side) other = road.getB();
            else if (road.getB() == side) other = road.getA();
        }

        return other;
    }

    /**
     * A way in: it stands by its own edge, pointing straight in, off the side's axis and the station's art.  On a
     * curved side it is shifted along the edge away from the curve, so the two ends of a curve never share a spot of
     * the chord (FR-111); on a straight side it is shifted off the axis, down or to the east.
     */
    private static Path2D apartChevron(Rectangle at, Side side, Side other, boolean bent)
    {
        double[] edge = middleOf(at, side);
        double[] toward = other == null ? edge : middleOf(at, other);

        double inX = Math.signum(at.getCenterX() - edge[0]);
        double inY = Math.signum(at.getCenterY() - edge[1]);

        if (!bent)
        {
            return TileAnnotation.arrivalShape(edge[0] + inX * EDGE + (inX != 0 ? 0 : ALONGSIDE),
                edge[1] + inY * EDGE + (inX != 0 ? ALONGSIDE : 0), new double[] {inX, inY}, CHEVRON);
        }

        // Along the edge, away from where the curve goes
        double lateral = inY != 0 ? -Math.signum(toward[0] - edge[0]) : 0;
        double longways = inX != 0 ? -Math.signum(toward[1] - edge[1]) : 0;

        return TileAnnotation.arrivalShape(edge[0] + inX * EDGE + lateral * BESIDE, edge[1] + inY * EDGE + longways * BESIDE,
            new double[] {inX, inY}, CHEVRON);
    }

    /** A next square's arrows at each side of its track that meets the station, where the diagram draws them. */
    private static void neighborMarks(List<Mark> out, Rectangle at, TileKey square, TileAnnotation their)
    {
        Map<Side, Boolean> open = new EnumMap<>(Side.class);

        for (TileAnnotation.Mark road : their.getMarks())
        {
            Direction way = road.getDirection();

            open.merge(road.getA(), way == Direction.BOTH || way == Direction.TOWARD_A, Boolean::logicalOr);
            open.merge(road.getB(), way == Direction.BOTH || way == Direction.TOWARD_B, Boolean::logicalOr);
        }

        for (Map.Entry<Side, Boolean> each : open.entrySet())
        {
            double[] edge = middleOf(at, each.getKey());

            // The arrowhead lies from the edge a third of the square inward
            double inX = (at.getCenterX() - edge[0]) / (SQUARE / 2.0);
            double inY = (at.getCenterY() - edge[1]) / (SQUARE / 2.0);

            double reach = SQUARE / 3.0;

            Shape area = new Ellipse2D.Double(edge[0] + inX * reach / 2 - reach / 2, edge[1] + inY * reach / 2 - reach / 2, reach,
                reach);

            out.add(new Mark(Mark.Kind.NEIGHBOR, square, each.getKey(), each.getValue(), area));
        }
    }

    private static double[] middleOf(Rectangle at, Side side)
    {
        switch (side)
        {
            case N: return new double[] {at.getCenterX(), at.y};
            case S: return new double[] {at.getCenterX(), at.y + at.height};
            case E: return new double[] {at.x + at.width, at.getCenterY()};
            default: return new double[] {at.x, at.getCenterY()};
        }
    }

    /** @return the mark a click at this point of the block presses, or null */
    Mark markAtForTest(Point at)
    {
        return block.at(at);
    }

    /** @return what the station square wears */
    TileAnnotation drawnForTest()
    {
        return drawn;
    }

    /** @return the sides a way in is drawn open at */
    Set<String> waysInForTest()
    {
        Set<String> sides = new TreeSet<>();

        for (Mark mark : marks())
        {
            if (mark.open && mark.kind == Mark.Kind.IN) sides.add(mark.side.name());
        }

        return sides;
    }

    /** @return every mark with its open state, as "kind.side square:open" */
    Set<String> stateForTest()
    {
        Set<String> out = new TreeSet<>();

        for (Mark mark : marks()) out.add(mark.kind + "." + mark.side + " " + mark.square + ":" + mark.open);

        return out;
    }

    /** @return the station's square and each next square, by side ("" for the station) */
    Map<String, Rectangle> squaresForTest()
    {
        Map<String, Rectangle> out = new java.util.LinkedHashMap<>();

        out.put("", cell(null));

        for (Side side : next.keySet()) out.put(side.name(), cell(side));

        return out;
    }

    /** @return a side's axis where the station's track meets it: from the middle of its edge, one square long, inward */
    Line2D axisForTest(Side side)
    {
        Side other = square == null || !next.containsKey(side) ? null : otherEnd(side);

        if (other == null) return null;

        Rectangle at = cell(null);

        double[] edge = middleOf(at, side);
        double[] from = other == side ? new double[] {at.getCenterX(), at.getCenterY()} : middleOf(at, other);

        double length = Math.hypot(edge[0] - from[0], edge[1] - from[1]);

        return new Line2D.Double(edge[0], edge[1], edge[0] - (edge[0] - from[0]) / length * SQUARE,
            edge[1] - (edge[1] - from[1]) / length * SQUARE);
    }

    /** The squares and the marks over them: a signal in gray, so its lamps are not taken for arrows; nothing faded (D190). */
    private final class Block extends JPanel
    {
        Block()
        {
            super(null);

            setToolTipText("");

            addMouseListener(OnMouse.clicked(e ->
            {
                if (e.getButton() != MouseEvent.BUTTON1 || doors == null) return;

                Mark mark = at(e.getPoint());

                if (mark != null) doors.press(mark);
            }));
            addMouseMotionListener(new MouseMotionAdapter()
            {
                @Override
                public void mouseMoved(MouseEvent e)
                {
                    Mark mark = doors == null ? null : at(e.getPoint());
                    setCursor(mark != null && mark.kind == Mark.Kind.DEPART ? HandPointer.cursor() : null);
                }
            });
        }

        /**
         * The smallest mark whose shape holds the point, as marks on a curve overlap, and of two near in size (two ways
         * in) the one whose middle is nearer; failing one, the same among the marks near it.
         */
        private Mark at(Point point)
        {
            for (boolean near : new boolean[] {false, true})
            {
                Mark best = null;

                for (Mark mark : marks())
                {
                    if (mark.hit(point, near) && (best == null || before(mark, best, point))) best = mark;
                }

                if (best != null) return best;
            }

            return null;
        }

        private boolean before(Mark one, Mark other, Point point)
        {
            if (Math.max(one.size(), other.size()) < 1.5 * Math.min(one.size(), other.size()))
            {
                return one.apart(point) < other.apart(point);
            }

            return one.size() < other.size();
        }

        @Override
        public String getToolTipText(MouseEvent e)
        {
            Mark mark = doors == null ? null : at(e.getPoint());

            return mark == null ? null : AutonomyEditorPanel.wrapped(doors.tip(mark));
        }

        @Override
        public Dimension getPreferredSize()
        {
            Insets inside = getInsets();

            return new Dimension(3 * SQUARE + inside.left + inside.right, rows() * SQUARE + inside.top + inside.bottom);
        }

        @Override
        public void doLayout()
        {
            art.setBounds(0, 0, getWidth(), getHeight());

            if (square != null) square.setBounds(cell(null));

            for (Map.Entry<Side, LayoutLabel> each : next.entrySet()) each.getValue().setBounds(cell(each.getKey()));
        }

        @Override
        protected void paintChildren(Graphics g)
        {
            if (square == null) return;

            Graphics2D g2 = (Graphics2D) g.create();

            try
            {
                for (Map.Entry<Side, LayoutLabel> each : next.entrySet())
                {
                    Rectangle at = cell(each.getKey());

                    Graphics2D over = (Graphics2D) g2.create(at.x, at.y, at.width, at.height);

                    if (!each.getValue().getComponent().isSignal()) each.getValue().paint(over);
                    else org.traincontrol.util.ImageUtil.inGray(over, new Rectangle(at.getSize()), each.getValue()::paint);

                    TileAnnotation their = nextMarks.get(each.getKey());

                    if (their != null) their.paint(over, at.width, at.height);

                    over.dispose();
                }

                Rectangle middle = cell(null);

                Graphics2D station = (Graphics2D) g2.create(middle.x, middle.y, middle.width, middle.height);

                square.paint(station);

                station.dispose();

                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                for (Mark mark : marks())
                {
                    if (mark.kind == Mark.Kind.IN) TileAnnotation.paintArrival(g2, (Path2D) mark.shape, mark.open, CHEVRON);
                }
            }
            finally
            {
                g2.dispose();
            }
        }
    }
}
