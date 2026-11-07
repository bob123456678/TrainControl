package org.traincontrol.gui.autonomyeditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseListener;
import java.util.Collections;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.traincontrol.automation.StandingRules;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.InfoMark;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.util.I18n;

/**
 * The station as the track diagram draws it (FR-111): a real square of the diagram, wearing what the diagram's own pass
 * asks the session for.  Both kinds of arrow always, whichever the diagram's menu has on, as these are what the settings
 * change.  Round it, in the diagram's green, the ways in and out `Layout.standingRulesAt` found on the built railway.  It
 * holds no rule.
 */
final class StationPicture extends JPanel
{
    /** Two diagram squares' worth, as the arrows are the point. */
    private static final int SQUARE = 64;

    /** Room round the square for the ways in and out, each the size of the square's own arrows. */
    private static final int ROOM = SQUARE / 2;

    private final Ways holder = new Ways();

    private transient LayoutLabel square;
    private transient TileKey showing;
    private transient TileAnnotation drawn;

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

        holder.setName("station.picture.ways");
        holder.setBackground(Color.WHITE);
        holder.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(204, 204, 204)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        add(holder, BorderLayout.CENTER);
    }

    /** Puts the square's art up when the square changes, and what the setup says over it every time. */
    void show(AutonomySession session, TileKey tile, TrainControlUI main)
    {
        if (!tile.equals(showing))
        {
            LayoutDiagramComponent art = session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

            holder.removeAll();
            holder.ways(null);

            square = art == null || main == null ? null : new LayoutLabel(art, holder, SQUARE, main, false);

            if (square != null)
            {
                // A picture: the pointer over it is not over the diagram
                for (MouseListener each : square.getMouseListeners()) square.removeMouseListener(each);

                GridBagConstraints c = new GridBagConstraints();

                c.insets = new Insets(ROOM, ROOM, ROOM, ROOM);

                holder.add(square, c);
            }

            showing = tile;

            holder.revalidate();
        }

        setVisible(square != null);

        if (square == null) return;

        drawn = session.staticAnnotations(Collections.singleton(tile), true, true).get(tile);

        square.setAutonomyAnnotation(drawn);
    }

    /** The railway's answer about the square shown, or null where there is none: nothing is drawn round it then. */
    void showWays(StandingRules rules)
    {
        holder.ways(rules);
    }

    /** @return what the square wears */
    TileAnnotation drawnForTest()
    {
        return drawn;
    }

    /** @return the sides a way out, or in, is drawn at */
    Set<String> waysForTest(boolean out)
    {
        return out ? holder.out : holder.in;
    }

    /** The square's frame, drawing round the square a way in as an arrow at it and a way out as one leaving it. */
    private static final class Ways extends JPanel
    {
        private transient Set<String> in = Collections.emptySet();
        private transient Set<String> out = Collections.emptySet();

        Ways()
        {
            super(new GridBagLayout());
        }

        void ways(StandingRules rules)
        {
            in = rules == null ? Collections.<String>emptySet() : rules.waysIn();
            out = rules == null ? Collections.<String>emptySet() : rules.waysOut();

            // A way by no side the build named cannot be put anywhere, so none is guessed
            if (in.contains(StandingRules.NO_SIDE) || out.contains(StandingRules.NO_SIDE))
            {
                in = Collections.emptySet();
                out = Collections.emptySet();
            }

            repaint();
        }

        @Override
        protected void paintChildren(Graphics g)
        {
            super.paintChildren(g);

            if (getComponentCount() == 0) return;

            Graphics2D g2 = (Graphics2D) g.create();

            try
            {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                for (String side : new String[] {"N", "E", "S", "W"}) paintSide(g2, getComponent(0).getBounds(), side);
            }
            finally
            {
                g2.dispose();
            }
        }

        private void paintSide(Graphics2D g, Rectangle at, String side)
        {
            boolean comes = in.contains(side);
            boolean goes = out.contains(side);

            if (!comes && !goes) return;

            double[] away = "N".equals(side) ? new double[] {0, -1} : "S".equals(side) ? new double[] {0, 1}
                : "E".equals(side) ? new double[] {1, 0} : new double[] {-1, 0};

            double midX = at.x + at.width / 2.0 + away[0] * at.width / 2.0;
            double midY = at.y + at.height / 2.0 + away[1] * at.height / 2.0;

            // Side by side where a train may both come and go, as the diagram draws a link's two moves
            double apart = comes && goes ? SQUARE / 5.0 : 0;

            if (goes)
            {
                TileAnnotation.openArrow(g, new int[] {(int) Math.round(midX - away[1] * apart + away[0] * (ROOM - 4)),
                    (int) Math.round(midY + away[0] * apart + away[1] * (ROOM - 4))}, away, SQUARE);
            }

            if (comes)
            {
                TileAnnotation.openArrow(g, new int[] {(int) Math.round(midX + away[1] * apart + away[0] * 4),
                    (int) Math.round(midY - away[0] * apart + away[1] * 4)}, new double[] {-away[0], -away[1]}, SQUARE);
            }
        }
    }
}
