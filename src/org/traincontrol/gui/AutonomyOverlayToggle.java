package org.traincontrol.gui;

import java.awt.FlowLayout;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import org.traincontrol.util.I18n;

/**
 * The one autonomy control the track diagram tab has: whether the overlay is drawn.
 *
 * Deliberately a single checkbox.  The diagram tab is for watching the railway, and everything that
 * CHANGES autonomy lives in the Auto tab or the layout editor - so nothing here can be pressed by
 * accident while operating trains.
 *
 * Mounted as the diagram scroll pane's column header, a thin strip above the track that the pane
 * reserves anyway, so no generated layout is touched and the grid keeps the whole viewport.
 *
 * @author Adam
 */
public class AutonomyOverlayToggle extends JPanel
{
    private final TrainControlUI ui;
    private final JCheckBox show = new JCheckBox(I18n.t("autosetup.ui.chkShowAutonomy"), true);

    /**
     * @param ui the main window, whose monitor driver the checkbox switches
     */
    public AutonomyOverlayToggle(TrainControlUI ui)
    {
        super(new FlowLayout(FlowLayout.LEFT, 4, 0));

        this.ui = ui;

        setOpaque(false);

        // Toggling only stops the DRAWING.  The driver keeps its wiring so that switching back on shows
        // the current state immediately rather than waiting for the next train to move.
        show.setFocusable(false);
        show.setOpaque(false);
        show.addActionListener(e -> apply());

        add(show);
    }

    /**
     * Sets the checkbox, which also applies it - used when a configuration loads successfully, which is
     * the moment the user has said they want to see autonomy.
     *
     * @param selected
     */
    public void setSelected(boolean selected)
    {
        show.setSelected(selected);
        apply();
    }

    /**
     * @return whether the overlay is switched on
     */
    public boolean isShowing()
    {
        return show.isSelected();
    }

    private void apply()
    {
        ui.getDiagramMonitorDriver().setEnabled(show.isSelected());

        // Monitoring only paints while trains are moving, so before a run this checkbox appeared to do
        // nothing at all.  It also switches the static layer - stations, and any track that has been
        // restricted - which is what somebody looking at the diagram wants to see about their setup.
        ui.showStaticAutonomyLayer(show.isSelected());
    }
}
