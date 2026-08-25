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

    // What the strip looks like on a page autonomy has been told to ignore
    static final java.awt.Color EXCLUDED_BACKGROUND = new java.awt.Color(255, 232, 232);
    private static final java.awt.Color EXCLUDED_TEXT = new java.awt.Color(170, 0, 0);

    /**
     * How many things the checks have to say about the setup, and a way straight to the first of them.
     *
     * Here rather than on a tab because it is the one piece of autonomy state that stays true while
     * the user is doing something else: a setup with a problem in it has that problem whichever page
     * they are looking at, and a count they have to go and ask for is one nobody asks for.  Silent
     * when there is nothing to say, so a finished setup carries no ornament.
     */
    private final javax.swing.JLabel findings = new javax.swing.JLabel();

    // Shown in the checkbox's place on a page autonomy has been told to ignore
    private final javax.swing.JLabel left_out =
        new javax.swing.JLabel(I18n.t("autosetup.ui.labelPageLeftOut"));

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

        // Red, and a way out of it.  A page left out of autonomy is a deliberate setting, but it is
        // also the reason nothing on this page has stations, arrows or trains - so saying it quietly in
        // grey read as decoration, and the setting that causes it lives three levels into a menu the
        // user would have to know to open.  Clicking takes them there.
        //
        // The banner's font, like the findings count beside it, and not bold: the colour already says
        // this matters, and a bold red line shouts where a red line was enough.
        left_out.setFont(AutonomyBanner.MESSAGE_FONT);
        left_out.setForeground(EXCLUDED_TEXT);
        left_out.setVisible(false);
        left_out.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        left_out.setToolTipText(I18n.t("autosetup.ui.tooltipPageLeftOut"));

        left_out.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                ui.openAutonomyPagesMenu();
            }
        });

        left.add(left_out);

        // The banner's own font, because the two sit one above the other and say related things - the
        // banner names the state, the count says how much of it there is.  Two sizes read as two
        // unrelated notices stacked by accident.
        findings.setFont(AutonomyBanner.MESSAGE_FONT);
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

        // The banner's own insets, so the two bands read as one thing stacked.
        //
        // The banner pads by 4 above and below and 8 at the sides; this strip padded by 2 all round and
        // then let its FlowLayout add 4 more at the left, so its text began six pixels in against the
        // banner's eight and sat two pixels higher.  Small numbers, and the two bands are the same
        // colour block one above the other - a step of two pixels between them is exactly the sort of
        // thing that reads as "something is broken" without the reader being able to say what.
        //
        // Four here plus the FlowLayout's own four makes eight at the leading edge, matching.
        setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
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

        source = !loaded || excluded ? null
            : stop != null && stop.isEnabled() ? stop
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
     * Sizes another button exactly as the Start button in this strip is sized.
     *
     * So that the Load button in the banner above and the Start button that replaces it are the same
     * object as far as the eye is concerned: same font, same height, same insets, one directly above the
     * other.  The height is the checkbox’s less three pixels, which is what stops a button looking
     * larger than a checkbox of the same height - a button carries a border and a checkbox does not.
     *
     * Re-applied whenever the text changes, since the preferred width follows the label.
     *
     * @param button
     */
    public void styleAsRunButton(javax.swing.AbstractButton button)
    {
        if (button == null) return;

        button.setFont(start != null ? start.getFont() : show.getFont());
        button.setMargin(new java.awt.Insets(0, 10, 0, 10));

        // Cleared first: asking a component its preferred size after setting one reads back what was set
        button.setPreferredSize(null);

        java.awt.Dimension wanted = button.getPreferredSize();

        button.setPreferredSize(new java.awt.Dimension(wanted.width,
            Math.max(show.getPreferredSize().height - 3, 14)));
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
    public void setFindings(int pageErrors, int pageWarnings, int totalErrors, int totalWarnings,
        org.traincontrol.automationui.TileGraph.TileKey first)
    {
        firstFinding = first;

        // Remembered, so that the banner appearing or going can re-decide whether to show this without
        // the count having to be recomputed and handed over again
        lastPageErrors = pageErrors;
        lastPageWarnings = pageWarnings;
        lastTotalErrors = totalErrors;
        lastTotalWarnings = totalWarnings;

        if (totalErrors + totalWarnings == 0)
        {
            findings.setVisible(false);
            return;
        }

        // Nothing on a page autonomy takes no notice of.  The number would be about a setup this page
        // is not part of, next to a strip that has just said so.
        if (excluded)
        {
            findings.setVisible(false);
            return;
        }

        // Nor underneath a banner that is already saying the same thing in words.  "This setup cannot
        // run yet" with "Fix it" beside it, and a count of the same problems directly below, is one
        // piece of news told twice - and the banner is the half with the button.
        if (bannerShowing)
        {
            findings.setVisible(false);
            return;
        }

        // Red whenever there is an error ANYWHERE, not only on this page.  One error stops the whole
        // setup from running, so it is worth the colour wherever the reader happens to be looking -
        // this is the one thing on the strip that is not a statement about the page in front of them.
        findings.setForeground(totalErrors > 0
            ? new java.awt.Color(170, 0, 0) : new java.awt.Color(150, 95, 0));

        // Errors and warnings said SEPARATELY when there are errors (OB-090).
        //
        // This showed one number - the two added together - on the argument that the split says
        // nothing a reader can act on differently. Adam read it as the error count: "the autonomy
        // error count is 4 but shows as 8", with four of each. The arithmetic was right and the label
        // was not, and a number in red that means "errors and warnings" will be read as "errors" by
        // anybody who is not thinking about the label.
        //
        // The page figure survives as the third number rather than the first, because "how many are
        // here" is what you want AFTER knowing whether any of them stop the setup running.
        findings.setText(totalErrors > 0
            ? I18n.f("autosetup.ui.labelFindingsCountErrors", totalErrors, totalWarnings,
                pageErrors + pageWarnings)
            : I18n.f("autosetup.ui.labelFindingsCount", totalWarnings, pageWarnings));
        findings.setToolTipText(I18n.t("autosetup.ui.tooltipFindings"));
        findings.setVisible(true);
    }

    /**
     * Whether a configuration is actually running.
     *
     * The strip exists before one does now, so that the banner above it can say "this layout has a setup
     * nobody has loaded" - and the controls here are all about a setup that IS loaded.  A checkbox
     * offering to show an overlay of nothing, and a findings count of nothing, are worse than an empty
     * strip: they invite a click that does nothing and teach that the controls are unreliable.
     *
     * @param loaded
     */
    public void setLoaded(boolean loaded)
    {
        this.loaded = loaded;

        show.setVisible(loaded && !excluded);

        // Said whether or not anything is loaded.  It used to need a running configuration, so on a
        // layout with a setup nobody had loaded the strip went red and held nothing - a bare red line
        // under the banner, which reads as something having gone wrong with the drawing rather than as
        // a statement about the page.  Stacked under the banner is fine; an empty red band is not.
        left_out.setVisible(excluded);

        // The findings count deliberately survives.  It is how somebody finds out WHY a setup will not
        // load - a setup with a blocking problem refuses, so the state "exists but is not running" is
        // exactly the state its problems most need saying in.  Hiding it there left the user with a
        // banner offering to load something that would not load and nothing to say what was wrong.
        run.setVisible(loaded);

        if (loaded) syncRun();

        revalidate();
        repaint();
    }

    // whether a configuration is loaded and running
    private boolean loaded = true;

    /**
     * Says the page is left out of autonomy, in place of the controls that would act on it.
     *
     * The checkbox goes rather than being greyed: there is nothing on this page for it to show or
     * hide, and an unticked box invites the reader to tick it and wonder why nothing happened.  What
     * replaces it says the one thing worth knowing.  The run button stays - starting autonomy is not a
     * statement about the page being looked at.
     *
     * @param excluded
     */
    public void setPageExcluded(boolean excluded)
    {
        this.excluded = excluded;

        show.setVisible(loaded && !excluded);

        // Said whether or not anything is loaded.  It used to need a running configuration, so on a
        // layout with a setup nobody had loaded the strip went red and held nothing - a bare red line
        // under the banner, which reads as something having gone wrong with the drawing rather than as
        // a statement about the page.  Stacked under the banner is fine; an empty red band is not.
        left_out.setVisible(excluded);

        if (excluded) findings.setVisible(false);

        paintState();

        // No Start button on a page autonomy takes no notice of.  Starting is not a statement about the
        // page being looked at - which is exactly why it was left here before - but offered from a red
        // strip that has just said this page is left out, it reads as an offer to run THIS page, and the
        // one thing that will certainly not happen is a train moving on it.
        syncRun();
    }

    /**
     * Colours the strip for the state it is in.
     */
    private void paintState()
    {
        java.awt.Color background = excluded ? EXCLUDED_BACKGROUND : java.awt.Color.WHITE;

        setBackground(background);

        // The checkbox paints its own square, so it has to be told as well or it keeps the old colour
        show.setBackground(background);

        repaint();
    }

    // whether the page being shown is one autonomy has been told to ignore
    private boolean excluded;

    // whether the banner above this strip is currently saying something
    private boolean bannerShowing;

    private int lastPageErrors;
    private int lastPageWarnings;
    private int lastTotalErrors;
    private int lastTotalWarnings;

    /**
     * Tells the strip whether the banner above it is saying anything.
     *
     * @param showing
     */
    public void setBannerShowing(boolean showing)
    {
        if (this.bannerShowing == showing) return;

        this.bannerShowing = showing;

        // Re-decided from what was last worked out, so that the count comes back when the banner goes
        setFindings(lastPageErrors, lastPageWarnings, lastTotalErrors, lastTotalWarnings, firstFinding);
    }

    /**
     * Deliberately NOT called isShowing().
     *
     * That name belongs to java.awt.Component, and overriding it here answered "is the overlay ticked"
     * to a question Swing asks for something else entirely: JComponent.paintImmediately begins with
     * `if (!isShowing()) return`, and every child walks up through its parents' answers.  Untick the box
     * and this strip stopped repainting altogether - the box kept its ticked pixels, and the copy of the
     * Start button kept whatever words it last had.  The opacity and explicit repaint() worked around
     * the symptom for the checkbox and left the rest of the strip frozen.
     *
     * @return whether the overlay is switched on
     */
    public boolean isOverlayShown()
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
