package org.traincontrol.gui.autonomyeditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagLayout;
import java.awt.event.MouseListener;
import java.util.Collections;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.util.I18n;

/**
 * The station as the track diagram draws it (FR-111): a real square of the diagram, wearing what the diagram's own pass
 * asks the session for.  Both kinds of arrow always, whichever the diagram's menu has on, as these are what the settings
 * change.  It draws nothing itself and holds no rule.
 */
final class StationPicture extends JPanel
{
    /** Two diagram squares' worth, as the arrows are the point. */
    private static final int SQUARE = 64;

    private final JPanel holder = new JPanel(new GridBagLayout());

    private transient LayoutLabel square;
    private transient TileKey showing;
    private transient TileAnnotation drawn;

    StationPicture()
    {
        super(new BorderLayout(0, 6));

        setName("station.picture");

        JLabel heading = UiStyle.label(I18n.t("autosetup.ui.stationPictureHeading"));

        add(heading, BorderLayout.NORTH);

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

            square = art == null || main == null ? null : new LayoutLabel(art, holder, SQUARE, main, false);

            if (square != null)
            {
                // A picture: the pointer over it is not over the diagram
                for (MouseListener each : square.getMouseListeners()) square.removeMouseListener(each);

                holder.add(square);
            }

            showing = tile;

            holder.revalidate();
        }

        setVisible(square != null);

        if (square == null) return;

        drawn = session.staticAnnotations(Collections.singleton(tile), true, true).get(tile);

        square.setAutonomyAnnotation(drawn);
    }

    /** @return what the square wears */
    TileAnnotation drawnForTest()
    {
        return drawn;
    }
}
