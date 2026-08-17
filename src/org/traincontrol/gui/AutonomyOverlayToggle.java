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
     * How many things the checks have to say about the setup, and a way straight to the first of them.
     *
     * Here rather than on a tab because it is the one piece of autonomy state that stays true while
     * the user is doing something else: a setup with a problem in it has that problem whichever page
     * they are looking at, and a count they have to go and ask for is one nobody asks for.  Silent
     * when there is nothing to say, so a finished setup carries no ornament.
     */
    private final javax.swing.JLabel findings = new javax.swing.JLabel();

    /**
     * @param ui the main window, whose monitor driver the checkbox switches
     */
    public AutonomyOverlayToggle(TrainControlUI ui)
    {
        super(new FlowLayout(FlowLayout.LEFT, 4, 0));

        this.ui = ui;

        // Opaque, and the same white as the diagram beneath it.  A transparent column header has
        // nothing painting behind it, so unticking the box left the old ticked pixels on screen: the
        // checkbox appeared stuck on even though the overlay had switched off underneath.
        setOpaque(true);
        setBackground(java.awt.Color.WHITE);

        // Toggling only stops the DRAWING.  The driver keeps its wiring so that switching back on shows
        // the current state immediately rather than waiting for the next train to move.
        show.setFocusable(false);
        show.setOpaque(false);
        show.addActionListener(e -> apply());

        add(show);

        findings.setFont(show.getFont());
        findings.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        findings.setVisible(false);
        findings.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 10, 0, 0));

        findings.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                ui.openAutonomyEditor(firstFinding);
            }
        });

        add(findings);

        // Belt and braces over show.setFocusable(false) above: this strip sits inside the main window,
        // where bare key presses drive locomotives, so nothing in it may hold the keyboard.
        AutonomyViewerPanel.unfocusable(this);
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
     * The square the count leads to: the first thing the checks found, in their own order, which puts
     * errors before warnings.
     */
    private org.traincontrol.automationui.TileGraph.TileKey firstFinding;

    /**
     * Shows what the checks currently say.
     *
     * @param errors how many things are wrong
     * @param warnings how many are worth looking at
     * @param first the square to open when the count is clicked, or null for none
     */
    public void setFindings(int errors, int warnings,
        org.traincontrol.automationui.TileGraph.TileKey first)
    {
        firstFinding = first;

        if (errors + warnings == 0)
        {
            findings.setVisible(false);
            return;
        }

        // Errors first and in their own colour, because one of them means the setup will not run at
        // all, while a page of warnings still will.
        findings.setForeground(errors > 0
            ? new java.awt.Color(170, 0, 0) : new java.awt.Color(150, 95, 0));

        findings.setText(errors > 0
            ? I18n.f("autosetup.ui.labelFindingsErrors", errors, warnings)
            : I18n.f("autosetup.ui.labelFindingsWarnings", warnings));

        findings.setToolTipText(I18n.t("autosetup.ui.tooltipFindings"));
        findings.setVisible(true);
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
