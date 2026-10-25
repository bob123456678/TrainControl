package org.traincontrol.gui;

import java.awt.FlowLayout;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import org.traincontrol.gui.autonomy.AutonomyBanner;
import org.traincontrol.util.I18n;

/**
 * The one autonomy control the track diagram tab has: whether the overlay is drawn.  Deliberately a single checkbox:
 * everything that changes autonomy lives in the Auto tab or the layout editor, so nothing here can be pressed by
 * accident while operating trains.  A strip above the diagram, a sibling of the scroll pane since OB-148, so it does
 * not scroll with the track.
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
     * How many things the checks have to say about the setup, and a way straight to the first of them.  Here, not on a
     * tab, because a setup's problem is true whichever page is shown.  Silent when there is nothing to say.
     */
    private final javax.swing.JLabel findings = new javax.swing.JLabel();

    // Shown in the checkbox's place on a page autonomy has been told to ignore
    private final javax.swing.JLabel left_out =
        new javax.swing.JLabel(I18n.t("autosetup.ui.labelPageLeftOut"));

    /**
     * Said while the setup has changes the railway has not taken yet ("Add the setup notice, but make it brief"): an
     * edit made as a run started, or one that leaves the setup unable to build while the last railway that built stays
     * loaded.  The tooltip says when they apply.
     */
    private final javax.swing.JLabel waiting = new javax.swing.JLabel(I18n.t("autosetup.ui.labelSetupNotApplied"));

    // whether the setup has changes the railway has not taken
    private boolean setupWaiting;

    /**
     * Starting and stopping autonomy, where the trains are.  A copy: it carries no opinion about when autonomy may run
     * (a dozen answers live in the main window) and shows what the real button is: its words, color and availability.
     */
    private final javax.swing.JButton run = new javax.swing.JButton();

    /** Whichever real button this is standing in for, or null when neither is available (nothing is drawn). */
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

        // Opaque, the white of the diagram beneath: a transparent strip left the old ticked pixels on screen after
        // unticking.
        setOpaque(true);
        setBackground(java.awt.Color.WHITE);

        // Toggling only stops the DRAWING, so switching back on shows the current state at once.
        show.setFocusable(false);

        // Opaque and painting its own white: making the strip opaque was not enough, the box has to clear its own
        // square.
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

        // Red, and a way out of it: a page left out of autonomy is why nothing on it has stations, arrows or trains,
        // and the setting lives three menus deep.  The banner's font, not bold: the color already says it matters.
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
                // Straight to the editor, not the Pages menu: "just make it attempt to click the edit button if it's
                // enabled ... since there is no entry for disabled pages".
                ui.openAutonomyEditorIfItCan();
            }
        });

        left.add(left_out);

        // The banner's own font: two sizes would read as two unrelated notices stacked by accident.
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

        // THE SETUP NOT YET APPLIED, in the warnings' amber: worth knowing, and not a fault
        waiting.setFont(AutonomyBanner.MESSAGE_FONT);
        waiting.setForeground(new java.awt.Color(150, 95, 0));
        waiting.setToolTipText(I18n.t("autosetup.ui.tooltipSetupNotApplied"));
        waiting.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 10, 0, 0));
        waiting.setVisible(false);

        left.add(waiting);

        add(left, java.awt.BorderLayout.WEST);

        // A copy of the main window's Start button, not a second implementation: pressing this presses that one.
        run.setFocusable(false);
        run.setVisible(false);
        run.setMargin(BUTTON_MARGIN);
        run.addActionListener(e ->
        {
            // Fixing is not starting: this one goes to the editor, at the first thing found, exactly
            // as the count beside it does.
            if (fixing)
            {
                ui.openAutonomyEditor(firstFinding);
                return;
            }

            if (source != null) source.doClick();
        });

        // The banner's own insets, so the two bands read as one thing stacked: 4 here plus the FlowLayout's 4 makes 8
        // at the leading edge.  Two above and below, not four (OB-149, OB-151), symmetric so the content stays
        // centered.
        setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        right.add(run);

        add(right, java.awt.BorderLayout.EAST);

        // Belt and braces over show.setFocusable(false) above: this strip sits inside the main window,
        // where bare key presses drive locomotives, so nothing in it may hold the keyboard.
        AutonomyViewerPanel.unfocusable(this);
    }

    /**
     * Sets the checkbox, which also applies it; used when a configuration loads successfully.
     *
     * @param selected
     */
    public void setSelected(boolean selected)
    {
        show.setSelected(selected);
        apply();
    }

    /**
     * Which of the two real buttons this strip is standing in for, if either.
     */
    public enum RunButton
    {
        /** Nothing to offer - no setup loaded, or this page is not part of it. */
        HIDDEN,

        /** Autonomy can be started. */
        START,

        /** Autonomy is running and can be stopped. */
        STOP,

        /** A stop has been asked for and the trains are still finishing: shown, but grayed. */
        STOP_PENDING
    }

    /**
     * What the strip should be showing, as a rule rather than a side effect (OB-143): "the button disappears rather
     * than being greyed out prior to replacement by the start button. make it get greyed out and then replaced."
     * Graceful Stop disables the real button at once while autonomy keeps running, and the strip used to hide itself
     * for that window.  STOP_PENDING only when a stop is genuinely pending (`isGracefulStopPending`): "neither enabled"
     * has other causes, mostly before anything ran.  Extracted so it can be exercised without a window.
     *
     * @param loaded whether a setup is loaded
     * @param excluded whether the page on screen is left out of it
     * @param stopEnabled whether the real Graceful Stop is enabled
     * @param startEnabled whether the real Start is enabled
     * @param stopPending whether a graceful stop is still being carried out
     * @return what to show
     */
    public static RunButton runButtonFor(boolean loaded, boolean excluded, boolean stopEnabled,
        boolean startEnabled, boolean stopPending)
    {
        if (!loaded || excluded) return RunButton.HIDDEN;

        // Stop wins when both are: once autonomy is running, stopping is the only thing left to offer.
        if (stopEnabled) return RunButton.STOP;

        if (startEnabled) return RunButton.START;

        return stopPending ? RunButton.STOP_PENDING : RunButton.HIDDEN;
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

    /** Matches the copy to whichever real button is available; stop wins when both are. */
    public final void syncRun()
    {
        // Posted to the event thread when it is not already there: several callers run inside autonomy's own threads.
        if (!javax.swing.SwingUtilities.isEventDispatchThread())
        {
            javax.swing.SwingUtilities.invokeLater(() -> syncRun());
            return;
        }

        RunButton showing = runButtonFor(loaded, excluded,
            stop != null && stop.isEnabled(),
            start != null && start.isEnabled(),
            ui != null && ui.isGracefulStopPending());

        source = showing == RunButton.START ? start
            : showing == RunButton.STOP || showing == RunButton.STOP_PENDING ? stop : null;

        // Fix it rather than Start, when starting would be refused (OB-090: "the setup refuses to run when there are
        // errors.  it runs on warnings, but on errors it should say fix it.").  Against START only: an error appearing
        // while trains run is when stopping matters most, so Stop keeps winning.  The guard's own question,
        // hasErrors(), not a count: it also covers a graph that will not build, and a count offered Start while every
        // press was refused.  lastTotalErrors is read only when there is no window to ask (a strip built before its
        // owner).
        boolean broken = ui != null ? ui.autonomyHasErrors() : lastTotalErrors > 0;

        fixing = source != null && source == start && broken;

        // The band's color follows this, and errors appear and go without anything else about the strip moving.
        paintState();

        if (source == null)
        {
            run.setVisible(false);
            revalidate();
            repaint();

            return;
        }

        run.setText(fixing ? I18n.t("autosetup.ui.btnFixSetup") : source.getText());

        run.setToolTipText(fixing
            ? I18n.t("autosetup.ui.tooltipFixSetup") : source.getToolTipText());

        run.setFont(source.getFont());

        // The turning mark, whichever button this stands in for ("Can we show the spinner next to start/graceful stop
        // on the track diagram viewer page as well?"): Start's own, grayed too during the coast-down (OB-309).
        javax.swing.Icon mark = start != null ? start.getIcon() : null;

        run.setIcon(mark);
        run.setDisabledIcon(mark);

        // White, with the strip behind it carrying the color (MT-173): "I would like a shaded background in light
        // yellow when fix it is active.  then change the color of the fix it button back to white."
        run.setBackground(fixing ? java.awt.Color.WHITE : source.getBackground());

        // Held to the checkbox's height, or the strip's tallest child pushes it down.  Cleared first: a preferred size
        // read after setting one is just what was set.
        run.setPreferredSize(null);

        java.awt.Dimension wanted = run.getPreferredSize();

        // The page's own button height (OB-313): the checkbox's font follows display scaling, the page's buttons do
        // not.
        run.setPreferredSize(new java.awt.Dimension(wanted.width, BUTTON_HEIGHT));

        // Grayed, not gone, while the stop it carries is being carried out (OB-143): requestStopAutonomy throws "stop
        // already issued" at this moment, so refusing the click says it without a dialog.
        run.setEnabled(showing != RunButton.STOP_PENDING);

        run.setVisible(true);

        revalidate();
        repaint();
    }

    /** Repaints the strip's button while it carries the turning mark, each step of the mark. */
    public void repaintRunningMark()
    {
        if (run.isVisible() && run.getIcon() != null) run.repaint();
    }

    /**
     * @return whether the strip's button is showing that something runs (OB-309)
     */
    public boolean isShowingSomethingRuns()
    {
        return run.isVisible() && run.getIcon() != null;
    }

    /**
     * @return whether the turning mark fits inside the strip's button, which is held to the checkbox's height
     */
    public boolean runningMarkFits()
    {
        java.awt.Insets in = run.getInsets();

        return run.getIcon() != null
            && run.getIcon().getIconHeight() <= run.getPreferredSize().height - in.top - in.bottom;
    }

    /**
     * Sizes another button exactly as the Start button in this strip is sized, so the Load button in the banner above
     * and the Start that replaces it look the same: same font, height (`BUTTON_HEIGHT`, OB-313) and insets.
     * Re-applied whenever the text changes, since the preferred width follows the label.
     *
     * @param button
     */
    public void styleAsRunButton(javax.swing.AbstractButton button)
    {
        if (button == null) return;

        button.setFont(start != null ? start.getFont() : show.getFont());
        button.setMargin(BUTTON_MARGIN);

        // Cleared first: asking a component its preferred size after setting one reads back what was set
        button.setPreferredSize(null);

        java.awt.Dimension wanted = button.getPreferredSize();

        button.setPreferredSize(new java.awt.Dimension(wanted.width, BUTTON_HEIGHT));
    }

    /**
     * The height of the buttons on this page (OB-313: "The issue was on lower display scaling monitors, where the
     * button looked too narrow vertically"); the main window's Edit, Small and Large are held to 24 by the form.
     */
    static final int BUTTON_HEIGHT = 24;

    /**
     * The padding round a strip button's text: the page's buttons' two above and below, and ten at the sides (OB-313).
     */
    private static final java.awt.Insets BUTTON_MARGIN = new java.awt.Insets(2, 10, 2, 10);

    /** The square the count leads to: the first thing the checks found, errors before warnings. */
    private org.traincontrol.automationui.TileGraph.TileKey firstFinding;

    /** Whether the run button is currently offering to fix the setup rather than to start it. */
    private boolean fixing;

    /**
     * The strip's fill while the setup has something that must be dealt with: light yellow, so black text reads across
     * the full width.
     */
    private static final java.awt.Color FIX_BACKGROUND = new java.awt.Color(255, 248, 205);

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

        // The run button turns on the error count, so it is re-decided here: errors appear and go without either button
        // moving.
        syncRun();

        if (totalErrors + totalWarnings == 0)
        {
            findings.setVisible(false);
            return;
        }

        // Nothing on a page autonomy takes no notice of: the number would be about a setup this page is not part of.
        if (excluded)
        {
            findings.setVisible(false);
            return;
        }

        // Nor underneath a banner already saying the same thing in words.
        if (bannerShowing)
        {
            findings.setVisible(false);
            return;
        }

        // Red whenever there is an error anywhere: one error stops the whole setup running.
        findings.setForeground(totalErrors > 0
            ? new java.awt.Color(170, 0, 0) : new java.awt.Color(150, 95, 0));

        // Errors and warnings said separately when there are errors (OB-090): "the autonomy error count is 4 but shows
        // as 8", with four of each.  The page figure is the third number.
        findings.setText(findingsCountText(totalErrors, totalWarnings, totalErrors > 0 ? pageErrors + pageWarnings : pageWarnings));
        findings.setToolTipText(I18n.t("autosetup.ui.tooltipFindings"));
        findings.setVisible(true);
    }

    /**
     * The strip's count, in words that agree with each number ("1 warnings" read wrong, as "1 things" did).
     *
     * @param errors errors in the whole setup
     * @param warnings warnings in the whole setup
     * @param onThisPage how many of the ones counted are on the page shown
     * @return "2 errors, 1 warning - 1 on this page", or without the errors where there are none
     */
    public static String findingsCountText(int errors, int warnings, int onThisPage)
    {
        // In the language's own forms: Polish has one for 2 to 4, French counts 0 with the singular
        String warned = I18n.f("autosetup.ui.countWarnings" + I18n.countForm(warnings), warnings);
        String here = I18n.f("autosetup.ui.countOnThisPage", onThisPage);

        if (errors <= 0) return I18n.f("autosetup.ui.labelFindingsCount", warned, here);

        String erred = I18n.f("autosetup.ui.countErrors" + I18n.countForm(errors), errors);

        return I18n.f("autosetup.ui.labelFindingsCountErrors", erred, warned, here);
    }

    /**
     * Whether a configuration is actually running.  The strip exists before one does, so the banner above can say a
     * setup is not loaded; the controls here are about a loaded setup and would invite clicks that do nothing.
     *
     * @param loaded
     */
    public void setLoaded(boolean loaded)
    {
        this.loaded = loaded;

        show.setVisible(loaded && !excluded);

        // Said whether or not anything is loaded, stacked under the banner; an empty red band is what to avoid.
        left_out.setVisible(excluded);

        // The findings count deliberately survives: it is how somebody finds out why a setup will not load.
        run.setVisible(loaded);

        if (loaded) syncRun();

        revalidate();
        repaint();
    }

    // whether a configuration is loaded and running
    private boolean loaded = true;

    /**
     * Says the page is left out of autonomy, in place of the controls that would act on it.  The checkbox goes rather
     * than being grayed: an unticked box invites a tick that visibly does nothing.  The run button stays.
     *
     * @param excluded
     */
    public void setPageExcluded(boolean excluded)
    {
        this.excluded = excluded;

        show.setVisible(loaded && !excluded);

        // Said whether or not anything is loaded, stacked under the banner; an empty red band is what to avoid.
        left_out.setVisible(excluded);

        if (excluded) findings.setVisible(false);

        // and the setup notice with it: the strip on a page left out says that, and only that
        waiting.setVisible(setupWaiting && !excluded);

        paintState();

        // No Start button on a page autonomy takes no notice of: offered from a red strip saying this page is left out,
        // it reads as an offer to run this page.
        syncRun();
    }

    /**
     * Says, or stops saying, that the setup has changes the railway has not taken yet - see `waiting`.
     *
     * @param newer whether it has
     */
    public void setSetupWaiting(boolean newer)
    {
        setupWaiting = newer;

        waiting.setVisible(newer && !excluded);

        paintState();

        revalidate();
        repaint();
    }

    /**
     * @return whether the strip is saying the setup has changes the railway has not taken yet
     */
    public boolean isSetupWaitingShown()
    {
        return waiting.isVisible();
    }

    /**
     * The strip itself goes when it has nothing in it (MT-219): "remove the gap below the gray bar and the track
     * diagram when there is autonomy but not loaded yet."  Asked of the children rather than the state, which two
     * setters and a repaint decide, so a fourth copy of "is there anything worth showing" cannot drift.
     */
    private void hideIfEmpty()
    {
        boolean anything = show.isVisible() || left_out.isVisible()
            || findings.isVisible() || run.isVisible() || waiting.isVisible();

        if (isVisible() == anything) return;

        setVisible(anything);

        revalidate();
        repaint();
    }

    /**
     * Colors the strip for the state it is in.
     */
    private void paintState()
    {
        hideIfEmpty();

        // Excluded wins; the two cannot both be true (an excluded page shows no run button), but the order is explicit.
        java.awt.Color background = excluded ? EXCLUDED_BACKGROUND
            : fixing ? FIX_BACKGROUND : java.awt.Color.WHITE;

        setBackground(background);

        // The checkbox paints its own square, so it has to be told as well or it keeps the old color
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
     * Deliberately NOT called isShowing(): that name belongs to java.awt.Component, and JComponent.paintImmediately
     * begins with `if (!isShowing()) return`, so unticking the box froze the strip's repainting altogether.
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

        // Monitoring only paints while trains move, so also switch the static layer (stations, restricted track), or
        // the checkbox does nothing before a run.
        ui.showStaticAutonomyLayer(show.isSelected());
    }
}
