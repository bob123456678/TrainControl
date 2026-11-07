package org.traincontrol.gui.autonomyeditor;

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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
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
import javax.swing.JComponent;
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
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.util.I18n;

/**
 * The station and the track squares its track meets, as the track diagram draws them at 60 pixels (FR-111).  The
 * station wears only its ways out (the diagram's green arrow, or its red outline) and its ways in to stop (the arrival
 * chevron, gray where barred), from `Layout.standingRulesAt`; the next squares wear only their own arrows on the track
 * that meets the station, over art drawn in gray so that only arrows carry color.  A click on a mark is handed to the
 * window, which presses the control that owns that setting.  It holds no rule.
 */
final class StationPicture extends JPanel
{
    /** The track diagram's 60 pixel square. */
    static final int SQUARE = 60;

    /** The ways in are drawn smaller than the diagram's, so that a way in and a way out fit on one side's axis. */
    private static final int CHEVRON = 40;

    /** Between a way out's back and the way in's base, along the axis. */
    private static final double APART = 2;

    /** What a click on a mark does, and what its tooltip says: the window's, as the window owns the controls. */
    interface Doors
    {
        /** @return what a click on this mark will do, or why it won't */
        String tip(Mark mark);

        void press(Mark mark);
    }

    /** One clickable mark: a station's way out or in at a side, or a next square's arrow at one of its sides. */
    static final class Mark
    {
        enum Kind
        {
            OUT, IN, NEIGHBOR
        }

        final Kind kind;
        final TileKey square;
        final Side side;
        final boolean open;
        final Shape shape;

        /** For a station's mark: where its arrowhead points from, and which way. */
        final int[] target;
        final double[] heading;

        Mark(Kind kind, TileKey square, Side side, boolean open, Shape shape, int[] target, double[] heading)
        {
            this.kind = kind;
            this.square = square;
            this.side = side;
            this.open = open;
            this.shape = shape;
            this.target = target;
            this.heading = heading;
        }

        boolean hit(Point at)
        {
            Rectangle area = shape.getBounds();

            area.grow(3, 3);

            return area.contains(at);
        }
    }

    private final Block block = new Block();

    private transient AutonomySession session;
    private transient TileKey showing;
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
        heading.add(UiStyle.label(I18n.t("autosetup.ui.stationPictureHeading")));
        heading.add(Box.createHorizontalStrut(4));

        JLabel mark = InfoMark.label(I18n.t("autosetup.ui.stationPictureHelp"));

        mark.setFocusable(true);

        heading.add(mark);

        add(heading, BorderLayout.NORTH);

        block.setName("station.picture.ways");
        block.setBackground(Color.WHITE);
        block.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(204, 204, 204)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        add(block, BorderLayout.CENTER);
    }

    /** Puts the squares up when the station changes, and what the setup says on them every time. */
    void show(AutonomySession session, TileKey tile, TrainControlUI main, Doors doors)
    {
        this.session = session;
        this.doors = doors;

        if (!tile.equals(showing))
        {
            block.removeAll();
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

        setVisible(square != null);

        if (square == null) return;

        TileAnnotation diagram = session.staticAnnotations(Collections.singleton(tile), true, true).get(tile);

        drawn = new TileAnnotation(null, -1, false, diagram == null ? null : diagram.getBadge(), false);

        square.setAutonomyAnnotation(drawn);

        nextMarks.clear();

        List<TileKey> squares = new ArrayList<>();

        for (Side side : next.keySet()) squares.add(tile.beside(side));

        Map<TileKey, TileAnnotation> theirs = session.staticAnnotations(squares, true, true);

        for (Side side : next.keySet())
        {
            TileAnnotation their = theirs.get(tile.beside(side));

            if (their == null) continue;

            // Only the arrows of the track that meets the station
            List<TileAnnotation.Mark> kept = new ArrayList<>();

            for (TileAnnotation.Mark road : their.getMarks())
            {
                if (road.getA() == side.opposite() || road.getB() == side.opposite()) kept.add(road);
            }

            if (!kept.isEmpty())
            {
                nextMarks.put(side, new TileAnnotation(kept, -1, false, null, false, their.isCurved()).withAllowedDirections());
            }
        }

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

    /** A square of the diagram, as a picture: the pointer over it is the picture's. */
    private LayoutLabel label(TileKey at, TrainControlUI main)
    {
        LayoutDiagramComponent art = session.getGraph() == null ? null : session.getGraph().getTiles().get(at);

        if (art == null || main == null) return null;

        LayoutLabel out = new LayoutLabel(art, block, SQUARE, main, false);

        for (MouseListener each : out.getMouseListeners()) out.removeMouseListener(each);
        for (MouseMotionListener each : out.getMouseMotionListeners()) out.removeMouseMotionListener(each);

        out.setToolTipText(null);

        block.add(out);

        return out;
    }

    /** Where a square sits: the station in the middle of a 3 by 3 block centered in the frame, a next square beside it. */
    private Rectangle cell(Side side)
    {
        Insets inside = block.getInsets();

        int left = inside.left + Math.max(0, (block.getWidth() - inside.left - inside.right - 3 * SQUARE) / 2);
        int top = inside.top + Math.max(0, (block.getHeight() - inside.top - inside.bottom - 3 * SQUARE) / 2);

        int x = side == Side.W ? 0 : side == Side.E ? 2 : 1;
        int y = side == Side.N ? 0 : side == Side.S ? 2 : 1;

        return new Rectangle(left + x * SQUARE, top + y * SQUARE, SQUARE, SQUARE);
    }

    /** Every mark, where it is drawn and clicked; one list for painting, clicks, tooltips and tests. */
    List<Mark> marks()
    {
        List<Mark> out = new ArrayList<>();

        if (square == null) return out;

        Rectangle middle = cell(null);

        for (Side side : next.keySet())
        {
            if (rules != null) stationMarks(out, middle, side);

            TileAnnotation their = nextMarks.get(side);

            if (their != null) neighborMarks(out, cell(side), showing.beside(side), their);
        }

        return out;
    }

    /**
     * A station side's two marks on one axis, the track's line from the side's middle into the square: the way out with
     * its tip at the edge, where the diagram draws it, and the way in just behind it, pointing in.
     */
    private void stationMarks(List<Mark> out, Rectangle at, Side side)
    {
        Side other = null;

        for (TilePorts.Route road : session.getRoutes(showing).values())
        {
            if (road.getA() == side) other = road.getB();
            else if (road.getB() == side) other = road.getA();
        }

        double[] edge = middleOf(at, side);
        double[] from = other == null || other == side ? new double[] {at.getCenterX(), at.getCenterY()} : middleOf(at, other);

        double dx = edge[0] - from[0];
        double dy = edge[1] - from[1];
        double length = Math.sqrt(dx * dx + dy * dy);

        double[] away = {dx / length, dy / length};

        int[] tip = {(int) Math.round(edge[0]), (int) Math.round(edge[1])};

        // Long enough for `arrowShape`, which asks for at least a pixel
        double[] heading = {away[0] * SQUARE, away[1] * SQUARE};

        boolean leaves = rules.waysOut().contains(side.name());

        java.awt.Polygon arrow = TileAnnotation.arrowShape(tip, heading, leaves, SQUARE);

        out.add(new Mark(Mark.Kind.OUT, showing, side, leaves, arrow, tip, heading));

        if (!session.arrivalSides(showing).contains(side)) return;

        // Behind the way out's back, measured off the arrowhead itself
        double back = 0;

        for (int i = 0; i < arrow.npoints; i++)
        {
            back = Math.max(back, (edge[0] - arrow.xpoints[i]) * away[0] + (edge[1] - arrow.ypoints[i]) * away[1]);
        }

        double[] inward = {-away[0], -away[1]};

        Path2D chevron = TileAnnotation.arrivalShape(edge[0] + inward[0] * (back + APART), edge[1] + inward[1] * (back + APART),
            inward, CHEVRON);

        out.add(new Mark(Mark.Kind.IN, showing, side, rules.waysIn().contains(side.name()), chevron, null, null));
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

            out.add(new Mark(Mark.Kind.NEIGHBOR, square, each.getKey(), each.getValue(), area, null, null));
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

    /** @return what the station square wears */
    TileAnnotation drawnForTest()
    {
        return drawn;
    }

    /** @return the sides a way out, or a way in, is drawn open at */
    Set<String> waysForTest(boolean out)
    {
        Set<String> sides = new TreeSet<>();

        for (Mark mark : marks())
        {
            if (mark.open && mark.kind == (out ? Mark.Kind.OUT : Mark.Kind.IN)) sides.add(mark.side.name());
        }

        return sides;
    }

    /** @return the station's square and each next square, by side ("" for the station) */
    Map<String, Rectangle> squaresForTest()
    {
        Map<String, Rectangle> out = new java.util.LinkedHashMap<>();

        out.put("", cell(null));

        for (Side side : next.keySet()) out.put(side.name(), cell(side));

        return out;
    }

    /** @return a side's axis: from the middle of the station's edge, one square long, into the station */
    Line2D axisForTest(Side side)
    {
        for (Mark mark : marks())
        {
            if (mark.kind == Mark.Kind.OUT && mark.side == side)
            {
                double length = Math.hypot(mark.heading[0], mark.heading[1]);

                return new Line2D.Double(mark.target[0], mark.target[1], mark.target[0] - mark.heading[0] / length * SQUARE,
                    mark.target[1] - mark.heading[1] / length * SQUARE);
            }
        }

        return null;
    }

    /** The squares, the next ones in gray, and the marks over them. */
    private final class Block extends JPanel
    {
        Block()
        {
            super(null);

            setToolTipText("");

            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    if (e.getButton() != MouseEvent.BUTTON1 || doors == null) return;

                    Mark mark = at(e.getPoint());

                    if (mark != null) doors.press(mark);
                }
            });
        }

        private Mark at(Point point)
        {
            for (Mark mark : marks())
            {
                if (mark.hit(point)) return mark;
            }

            return null;
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

            return new Dimension(3 * SQUARE + inside.left + inside.right, 3 * SQUARE + inside.top + inside.bottom);
        }

        @Override
        public void doLayout()
        {
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

                    inGray(g2, each.getValue(), at);

                    TileAnnotation their = nextMarks.get(each.getKey());

                    if (their != null)
                    {
                        Graphics2D over = (Graphics2D) g2.create(at.x, at.y, at.width, at.height);

                        their.paint(over, at.width, at.height);

                        over.dispose();
                    }
                }

                Rectangle middle = cell(null);

                Graphics2D station = (Graphics2D) g2.create(middle.x, middle.y, middle.width, middle.height);

                square.paint(station);

                station.dispose();

                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                for (Mark mark : marks())
                {
                    if (mark.kind == Mark.Kind.OUT) TileAnnotation.paintArrow(g2, mark.target, mark.heading, mark.open, SQUARE);

                    if (mark.kind == Mark.Kind.IN) TileAnnotation.paintArrival(g2, (Path2D) mark.shape, mark.open, CHEVRON);
                }
            }
            finally
            {
                g2.dispose();
            }
        }
    }

    /** A next square's art without its color, so a signal's lamps are not read as the station's arrows. */
    private static void inGray(Graphics2D g, JComponent part, Rectangle at)
    {
        double scale = Math.max(1, g.getTransform().getScaleX());

        BufferedImage image = new BufferedImage((int) Math.ceil(at.width * scale), (int) Math.ceil(at.height * scale),
            BufferedImage.TYPE_INT_ARGB);

        Graphics2D on = image.createGraphics();

        on.scale(scale, scale);
        part.paint(on);
        on.dispose();

        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                int argb = image.getRGB(x, y);
                int light = (((argb >> 16) & 0xff) * 299 + ((argb >> 8) & 0xff) * 587 + (argb & 0xff) * 114) / 1000;

                image.setRGB(x, y, (argb & 0xff000000) | light << 16 | light << 8 | light);
            }
        }

        g.drawImage(image, at.x, at.y, at.width, at.height, null);
    }
}
