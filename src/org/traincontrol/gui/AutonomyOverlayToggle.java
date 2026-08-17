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
     * Starting and stopping autonomy, where the trains are rather than on a tab.
     *
     * A copy in the strictest sense: it carries no opinion about when autonomy may run.  That question
     * has a dozen answers scattered through the main window - power off, nothing placed, a locomotive
     * still moving, a configuration not loaded, the tabs pulled - and a second implementation of it
     * would be wrong the first time any of them changed.  Instead this shows exactly what the real
     * button currently is: its words, its colour, and whether it is available at all.
     */
    private final javax.swing.JButton run = new javax.swing.JButton();

    /**
     * Whichever real button this is currently standing in for, or null when neither is available - in
     * which case nothing is drawn.
     */
    private javax.swing.AbstractButton source;

    private javax.swing.AbstractButton start;
    private javax.swing.AbstractButton stop;

    /**
     * @param ui the main window, whose monitor driver the checkbox switches
     */
    public AutonomyOverlayToggle(TrainControlUI ui)
    {
        super(new java.awt.BorderLayout());

        this.ui = ui;

        // Opaque, and the same white as the diagram beneath it.  A transparent column header has
        // nothing painting behind it, so unticking the box left the old ticked pixels on screen: the
        // checkbox appeared stuck on even though the overlay had switched off underneath.
        setOpaque(true);
        setBackground(java.awt.Color.WHITE);

        // Toggling only stops the DRAWING.  The driver keeps its wiring so that switching back on shows
        // the current state immediately rather than waiting for the next train to move.
        show.setFocusable(false);

        // Opaque, and painting its own white.  Transparent, it relied on an ancestor repainting the
        // area beneath it, and that did not happen here - so unticking the box left the old ticked
        // pixels on screen while the overlay switched off underneath.  Making the STRIP opaque was not
        // enough; the box has to clear its own square.
        show.setOpaque(true);
        show.setBackground(java.awt.Color.WHITE);

        show.addActionListener(e ->
        {
            apply();

            // and paint the new state, rather than trusting that something else will
            show.repaint();
        });

        // Checkbox and count to the left, the run button hard right, as the window's own toolbars do
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(show);

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

        left.add(findings);

        add(left, java.awt.BorderLayout.WEST);

        // A copy of the main window's own Start button, not a second implementation of it.  Pressing
        // this presses that one, so everything it does - the checks, the power, the log line, the
        // state the rest of the window then shows - happens exactly once and in one place.
        run.setFocusable(false);
        run.setVisible(false);
        run.setMargin(new java.awt.Insets(0, 10, 0, 10));
        run.addActionListener(e ->
        {
            if (source != null) source.doClick();
        });

        // The same 4px breathing room the checkbox gets from its own FlowLayout, so the two ends of
        // the strip are inset alike rather than one hugging the edge.  Two pixels above and below on
        // the strip itself, which is what stops the button touching the track drawn under it.
        setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 2, 2));

        // Two pixels of its own above the button.  The strip's border applies to both ends alike, and
        // the button needed slightly more room off the top edge than the checkbox does.
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        right.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 0, 0, 0));
        right.add(run);

        add(right, java.awt.BorderLayout.EAST);

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
     * Tells the strip which buttons it is standing in for.
     *
     * @param start the main window's Start Autonomous Operation
     * @param stop its Graceful Stop
     */
    public void bindRunButtons(javax.swing.AbstractButton start, javax.swing.AbstractButton stop)
    {
        this.start = start;
        this.stop = stop;

        syncRun();
    }

    /**
     * Matches the copy to whichever real button is available.
     *
     * Stop wins when both are: once autonomy is running, stopping it is the only thing left to offer,
     * and that is the moment the button has to change under the user's hand rather than the moment a
     * second button appears beside it.
     */
    public final void syncRun()
    {
        // Posted to the event thread when it is not already there.  Several of the places that switch
        // these buttons run inside autonomy's own threads - the return-home staging, the run loop - so
        // the property change that brings us here arrives on whichever thread made it, and touching
        // Swing from one of those is the kind of fault that shows up once a fortnight as a repaint
        // that did not happen.
        if (!javax.swing.SwingUtilities.isEventDispatchThread())
        {
            javax.swing.SwingUtilities.invokeLater(() -> syncRun());
            return;
        }

        source = stop != null && stop.isEnabled() ? stop
            : start != null && start.isEnabled() ? start : null;

        if (source == null)
        {
            run.setVisible(false);
            revalidate();
            repaint();

            return;
        }

        run.setText(source.getText());
        run.setToolTipText(source.getToolTipText());
        run.setFont(source.getFont());
        run.setBackground(source.getBackground());

        // Held to the checkbox's height.  This strip is the scroll pane's column header, so its height
        // is whatever its tallest child asks for - and a button at its natural size is taller than a
        // checkbox, which pushed the whole strip down and left the checkbox floating in the middle of
        // it.  Cleared first, because asking a component its preferred size after setting one just
        // reads back what was set.
        run.setPreferredSize(null);

        java.awt.Dimension wanted = run.getPreferredSize();

        // Three pixels under the checkbox's own height.  Matching it exactly still read as a large
        // button, because a button carries a border and a checkbox does not - so the same number of
        // pixels looks bigger on one than the other.
        run.setPreferredSize(new java.awt.Dimension(wanted.width,
            Math.max(show.getPreferredSize().height - 3, 14)));

        run.setVisible(true);

        revalidate();
        repaint();
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
