package org.traincontrol.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.autonomy.AutonomyReport;
import org.traincontrol.gui.widgets.QuickFileChooser;
import org.traincontrol.gui.widgets.DetailsArea;
import org.traincontrol.util.I18n;

/**
 * Managing a layout's autonomy configurations, from the Auto tab: choosing which one runs, moving them between
 * machines, and checking that nothing looks wrong before trusting trains to it.  Hand written and mounted into a
 * container the main window already has.
 *
 * @author Adam
 */
public class AutonomyViewerPanel extends JPanel
{
    /**
     * The window's current session, asked for every time: the window replaces its own whenever the layout changes, and
     * a panel holding the old one writes where nobody reads.  The construction-time session stands in when the window
     * answers null (a layout not stored locally), so callers never see null.
     *
     * @return the session the rest of the application is using
     */
    private AutonomySession session()
    {
        AutonomySession current = ui.getAutonomySession();

        return current != null ? current : initialSession;
    }

    private final AutonomySession initialSession;

    /**
     * The main window, parent of every dialog raised here: this panel is no longer on screen, and a dialog parented to
     * it would center on the display.
     */
    private final TrainControlUI ui;

    private final JComboBox<String> configurations = new JComboBox<>();
    private final DefaultListModel<String> roster = new DefaultListModel<>();
    private final DefaultListModel<String> findingsModel = new DefaultListModel<>();
    private final JList<String> findings = new JList<>(findingsModel);

    // Which tile each row of the findings list refers to; null for a page heading.  Parallel to the
    // model rather than a richer element type, so the list still renders as plain strings.
    private final List<org.traincontrol.automationui.TileGraph.TileKey> findingRows =
        new java.util.ArrayList<>();

    // The squares each row's finding names beside its own - a guard's signal - for the editor to outline when a click
    // opens it there.  Parallel to the rows; null where there are none.
    private final List<List<org.traincontrol.automationui.TileGraph.TileKey>> findingRelatedRows =
        new java.util.ArrayList<>();

    // What each findings row is, so the renderer can color it and a click can act on it.  Parallel
    // lists rather than a richer element type, so the list still renders as plain strings.
    private final List<AutonomyChecks.Severity> findingSeverity = new java.util.ArrayList<>();

    private final JButton initialize = new JButton(I18n.t("autosetup.ui.btnInitFromLayout"));
    private final JButton enable = new JButton(I18n.t("autosetup.ui.btnEnable"));
    private final JButton placeLocomotives =
        new JButton(I18n.t("autosetup.ui.btnPlaceLocomotives"));

    // Built in buildSteps rather than here, but held as a field so it can be hidden with the rest of
    // step 3 - it leads to a tab that does not exist until a configuration is running.
    private JButton startHere;

    private final JLabel stepChoose = step(I18n.t("autosetup.ui.stepChoose"));
    private final JLabel stepPages = step(I18n.t("autosetup.ui.stepPages"));
    private final JLabel stepEnable = step(I18n.t("autosetup.ui.stepEnable"));
    private final JLabel stepRun = step(I18n.t("autosetup.ui.stepRun"));

    private final JLabel pagesSummary = new JLabel();

    private final JLabel hint = new JLabel(I18n.t("autosetup.ui.hintClickToFix"));

    private final JLabel status = new JLabel();


    public AutonomyViewerPanel(AutonomySession session, TrainControlUI ui)
    {
        // Fallback for session(); every use asks the window for the current one.
        this.initialSession = session;
        this.ui = ui;

        // White, and no inset border: this panel stands inside a white container, and a default gray
        // JPanel with padding read as a second surface floating on top of it.
        setOpaque(true);
        setBackground(java.awt.Color.WHITE);
        setLayout(new BorderLayout(0, 6));

        add(buildSteps(), BorderLayout.NORTH);
        add(buildFindings(), BorderLayout.CENTER);

        // No preferred size: the scroll pane it lives in has scrolling off, so the viewport hands this panel the tab's
        // space and the findings list scrolls inside its own box.
        setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        unfocusable(this);

        refresh();
    }

    // Copied from the Autonomy Settings tab so the two read as one design: group headings Segoe UI Semibold 13 navy,
    // labels and controls plain 14, buttons bold 12, boxes white with a 1px (204,204,204) line border.
    static final java.awt.Font FONT = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14);

    static final java.awt.Font FONT_GROUP = new java.awt.Font("Segoe UI Semibold", java.awt.Font.PLAIN, 13);

    static final java.awt.Font FONT_BOLD = new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12);

    /** The findings and roster lists only: dense reading, and at 14 a real layout's list stops fitting. */
    static final java.awt.Font FONT_LIST = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12);

    public static final java.awt.Color HEADING_COLOUR = new java.awt.Color(0, 0, 115);
    static final java.awt.Color BOX_BORDER = new java.awt.Color(204, 204, 204);

    static final java.awt.Color ERROR_COLOUR = new java.awt.Color(170, 0, 0);
    static final java.awt.Color WARNING_COLOUR = new java.awt.Color(150, 95, 0);
    static final java.awt.Color SUBHEADING_COLOUR = new java.awt.Color(70, 70, 70);

    /**
     * Takes a whole panel out of the keyboard's reach, and returns it: the main window drives locomotives from bare key
     * presses, so a control that can take focus would swallow them.  Menus are untouched (a popup takes focus only
     * while open).
     *
     * @param component the root to sweep, usually a panel
     */
    static <T extends java.awt.Component> T unfocusable(T component)
    {
        component.setFocusable(false);

        if (component instanceof java.awt.Container)
        {
            for (java.awt.Component child : ((java.awt.Container) component).getComponents())
            {
                unfocusable(child);
            }
        }

        return component;
    }

    /** Applies the application's control font to a component, and returns it; bold for a button. */
    static <T extends javax.swing.JComponent> T styled(T component, boolean bold)
    {
        component.setFont(bold ? FONT_BOLD : FONT);
        return component;
    }

    /**
     * A group heading, sitting above a boxed panel - the "Train Behavior" style.
     */
    private JLabel group(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FONT_GROUP);
        label.setForeground(HEADING_COLOUR);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    /**
     * A field label inside a box - the "Minimum Action Delay (s)" style.
     */
    private JLabel step(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FONT);
        label.setForeground(HEADING_COLOUR);
        return label;
    }

    /**
     * A white box with the window's own hairline border, as the settings tab uses.
     */
    private JPanel box()
    {
        JPanel panel = new JPanel();
        panel.setBackground(java.awt.Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BOX_BORDER),
            BorderFactory.createEmptyBorder(8, 10, 10, 10)));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    /**
     * A row of controls that sits flush left under its field label.
     */
    private JPanel row()
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        return panel;
    }

    /** The step buttons share a width, so the box's left edge reads as one column. */
    private JButton sized(JButton button)
    {
        styled(button, true);
        button.setPreferredSize(new Dimension(BUTTON_WIDTH, 26));
        return button;
    }

    static final int BUTTON_WIDTH = 170;

    /**
     * The workflow (choose, scope, enable, run) as a boxed group, each step a label with its controls beneath on one
     * grid.
     */
    private JPanel buildSteps()
    {
        JPanel outer = new JPanel();
        outer.setOpaque(false);
        outer.setLayout(new javax.swing.BoxLayout(outer, javax.swing.BoxLayout.Y_AXIS));

        outer.add(group(I18n.t("autosetup.ui.headingConfiguration")));
        outer.add(javax.swing.Box.createVerticalStrut(3));

        JPanel panel = box();
        panel.setLayout(new java.awt.GridBagLayout());

        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = java.awt.GridBagConstraints.WEST;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        // --- step 1: which configuration ---
        add(panel, gbc, stepChoose, 0);

        initialize.addActionListener(e -> initialize());

        JPanel initRow = row();
        initRow.add(sized(initialize));
        add(panel, gbc, initRow, 0);

        JPanel choose = row();

        configurations.setFont(FONT);
        configurations.setMaximumRowCount(12);
        configurations.setPreferredSize(new Dimension(BUTTON_WIDTH, 26));
        choose.add(configurations);

        // Everything that manages configurations lives behind one button and the combo's context menu: they are rare.
        final JButton manage = new JButton(I18n.t("autosetup.ui.btnManage"));
        manage.addActionListener(e -> manageMenu().show(manage, 0, manage.getHeight()));
        choose.add(styled(manage, true));

        // No ActionListener, deliberately: a configuration is chosen from the menu, not by selecting here.  Anything
        // that adds one needs a guard around populate(), which sets the selection itself.
        configurations.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e)
            {
                if (javax.swing.SwingUtilities.isRightMouseButton(e))
                {
                    manageMenu().show(configurations, e.getX(), e.getY());
                }
            }
        });

        add(panel, gbc, choose, 0);

        // --- which pages count ---
        // On the surface, not the Manage menu: it decides which track is considered at all, so it is the first thing
        // to reach for when the findings are about a page that is not part of the railway.
        add(panel, gbc, stepPages, 10);

        JPanel pageRow = row();

        pageRow.add(styled(pagesSummary, false));

        add(panel, gbc, pageRow, 0);

        // --- step 2: turn it on ---
        add(panel, gbc, stepEnable, 10);

        enable.setToolTipText(I18n.t("autosetup.ui.tooltipLoadConfiguration"));
        enable.addActionListener(e ->
        {
            Object selected = configurations.getSelectedItem();

            if (selected != null) loadByRequest(String.valueOf(selected));
        });

        JPanel enableRow = row();
        enableRow.add(sized(enable));
        enableRow.add(styled(status, false));

        add(panel, gbc, enableRow, 0);

        // --- step 3: run ---
        add(panel, gbc, stepRun, 10);

        JPanel run = row();

        // Opens the diagram editor, where a station is right-clicked and given a locomotive.
        placeLocomotives.addActionListener(e -> ui.openAutonomyEditor(null));
        run.add(sized(placeLocomotives));

        // The step after placing, its own button so the run tab still has something pointing at it.
        startHere = new JButton(I18n.t("autosetup.ui.btnGoToStart"));
        startHere.setToolTipText(I18n.t("autosetup.ui.tooltipGoToStart"));
        startHere.addActionListener(e -> ui.showAutonomyRunTab());
        run.add(styled(startHere, true));

        add(panel, gbc, run, 0);

        outer.add(panel);

        return outer;
    }

    /**
     * Adds one full-width row to a GridBagLayout, with a gap above it.
     */
    private void add(JPanel panel, java.awt.GridBagConstraints gbc, java.awt.Component component,
        int gapAbove)
    {
        gbc.insets = new java.awt.Insets(gapAbove, 0, 2, 0);
        panel.add(component, gbc);
        gbc.gridy++;
    }

    /**
     * Everything that manages configurations, on one menu.
     */
    private javax.swing.JPopupMenu manageMenu()
    {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        menu.add(item(I18n.t("autosetup.ui.menuNewConfiguration"), new Runnable()
            { public void run() { duplicate(); } }));
        menu.add(item(I18n.t("autosetup.ui.menuRenameConfiguration"), new Runnable()
            { public void run() { rename(); } }));
        menu.add(item(I18n.t("autosetup.ui.menuDeleteConfiguration"), new Runnable()
            { public void run() { delete(); } }));
        menu.addSeparator();
        menu.add(item(I18n.t("autosetup.ui.btnImportConfiguration"), new Runnable()
            { public void run() { importConfiguration(); } }));
        menu.add(item(I18n.t("autosetup.ui.btnExportConfiguration"), new Runnable()
            { public void run() { exportConfiguration(); } }));

        // Debug builds only, last on the menu (the flag is read fresh: the menu is rebuilt on every press).  It writes
        // the derived graph as a diagnostic, so it is offered only with a setup free of blocking problems, as the
        // Autonomy menu does.
        if (ui.getModel() != null && ui.getModel().isDebug()
            && session().exists() && !session().hasBlockingProblems())
        {
            menu.addSeparator();
            menu.add(item(I18n.t("autosetup.ui.menuExportRawGraph"), new Runnable()
                { public void run() { inspect(); } }));
        }

        return menu;
    }

    private javax.swing.JMenuItem item(String text, final Runnable action)
    {
        javax.swing.JMenuItem menuItem = new javax.swing.JMenuItem(text);
        menuItem.addActionListener(e -> action.run());
        return styled(menuItem, false);
    }

    /**
     * The findings, errors first, each clickable; the one part of this tab allowed to overflow (it scrolls in its own
     * box).
     */
    private JPanel buildFindings()
    {
        JPanel outer = new JPanel(new BorderLayout(0, 3));
        outer.setOpaque(false);

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new javax.swing.BoxLayout(heading, javax.swing.BoxLayout.Y_AXIS));
        heading.add(group(I18n.t("autosetup.ui.headingFindings")));

        hint.setFont(FONT_LIST);
        hint.setForeground(SUBHEADING_COLOUR);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        heading.add(hint);

        outer.add(heading, BorderLayout.NORTH);

        JPanel panel = box();
        panel.setLayout(new BorderLayout(0, 6));

        findings.setCellRenderer(new FindingRenderer(findingSeverity, findingRows, FONT, FONT_BOLD));
        findings.setBackground(java.awt.Color.WHITE);
        findings.setFont(FONT_LIST);

        // One click, straight to the square it is about, with the tools that can fix it already open.
        findings.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                int row = findings.locationToIndex(e.getPoint());

                if (row < 0 || row >= findingRows.size()) return;

                // locationToIndex answers the CLOSEST row for a click below the last one, so check the cell bounds.
                java.awt.Rectangle bounds = findings.getCellBounds(row, row);

                if (bounds == null || !bounds.contains(e.getPoint())) return;

                // AND ITS TEXT ON THE CLIPBOARD, as the editor's list does (MT-767) - a notice only: a heading has no
                // severity
                if (row < findingSeverity.size() && findingSeverity.get(row) != null)
                {
                    AutonomyEditorPanel.copyNoticeToClipboard(findings.getModel().getElementAt(row));
                }

                org.traincontrol.automationui.TileGraph.TileKey tile = findingRows.get(row);

                // With the squares it names beside its own, outlined there as the editor's own list outlines them
                if (tile != null)
                {
                    ui.openAutonomyEditor(tile, row < findingRelatedRows.size() ? findingRelatedRows.get(row) : null);
                }
            }
        });

        // The findings are the only thing here genuinely wider than the tab, so this is the only thing
        // that scrolls sideways.  Everything else is bounded, so the tab itself never scrolls at all.
        JScrollPane scroll = new JScrollPane(findings,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(java.awt.Color.WHITE);

        panel.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 2));
        bottom.setOpaque(false);

        // A field label rather than a titled border: the settings tab labels its groups this way, and a
        // box around a list inside a boxed group is a frame inside a frame.
        bottom.add(step(I18n.t("autosetup.ui.labelLocomotiveRoster")), BorderLayout.NORTH);

        JList<String> locomotives = new JList<>(roster);
        locomotives.setFont(FONT_LIST);

        JScrollPane rosterScroll = new JScrollPane(locomotives,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        rosterScroll.setBorder(BorderFactory.createLineBorder(BOX_BORDER));
        rosterScroll.setPreferredSize(new Dimension(10, 80));
        rosterScroll.getViewport().setBackground(java.awt.Color.WHITE);

        bottom.add(rosterScroll, BorderLayout.CENTER);

        panel.add(bottom, BorderLayout.SOUTH);

        outer.add(panel, BorderLayout.CENTER);

        return outer;
    }

    /**
     * Colors a row by what it is: a section heading, something that must be fixed, or something worth
     * checking.  Color rather than a prefix, because the list is scanned rather than read.  The editor's list too.
     */
    static final class FindingRenderer extends javax.swing.DefaultListCellRenderer
    {
        private final List<AutonomyChecks.Severity> severities;
        private final List<?> tiles;
        private final java.awt.Font font;
        private final java.awt.Font headingFont;

        /**
         * @param severities each row's severity, null on a heading
         * @param tiles each row's square, null on a heading and on a row about no square
         */
        FindingRenderer(List<AutonomyChecks.Severity> severities, List<?> tiles, java.awt.Font font,
            java.awt.Font headingFont)
        {
            this.severities = severities;
            this.tiles = tiles;
            this.font = font;
            this.headingFont = headingFont;
        }

        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean isSelected, boolean cellHasFocus)
        {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            AutonomyChecks.Severity severity =
                index < severities.size() ? severities.get(index) : null;

            boolean heading = index < tiles.size()
                && tiles.get(index) == null && severity == null;

            setFont(heading ? headingFont : font);

            if (!isSelected)
            {
                setForeground(heading ? HEADING_COLOUR
                    : severity == AutonomyChecks.Severity.ERROR ? ERROR_COLOUR
                    : severity == AutonomyChecks.Severity.WARNING ? WARNING_COLOUR
                    : java.awt.Color.DARK_GRAY);
            }

            return this;
        }
    }

    // configurations

    /**
     * Points the Manage actions at a configuration, for a caller that has no combo box of its own.
     *
     * @param name
     */
    public void setSelectedConfiguration(String name)
    {
        configurations.setSelectedItem(name);
    }

    public String selected()
    {
        Object chosen = configurations.getSelectedItem();

        return chosen == null ? session().getStore().getActiveConfiguration() : String.valueOf(chosen);
    }

    /**
     * Loads what an import leaves in use: the configuration just imported where nothing was running, otherwise the
     * running one reloaded over the shared half the import merged (`configurationToLoadAfterImport`).  Interactive,
     * unlike the startup resume: the user just asked, so a failed build is reported now.
     *
     * @param name the configuration to bring up, or null to leave things alone
     */
    private void loadAfterImport(String name)
    {
        loadAfterImport(name, true);
    }

    /**
     * The same, told whether the running layout still has to be folded back in first - false where the import captured
     * it before writing, so the reload does not write it back over what the import brought.
     *
     * @param name the configuration to bring up, or null to leave things alone
     * @param captureRunningState whether the reload captures the running layout first
     */
    private void loadAfterImport(String name, boolean captureRunningState)
    {
        if (name == null || name.trim().isEmpty()) return;

        // The rule lives on AutonomySession, where it can be tested; see the note there for why
        // something already running has to be loaded AGAIN rather than left alone.
        String toLoad = AutonomySession.configurationToLoadAfterImport(
            ui.getActiveDiagramConfiguration(), name);

        if (toLoad == null) return;

        session().getStore().setActiveConfiguration(toLoad);

        load(toLoad, true, captureRunningState);

        // Rebuild the diagram, which makes the captions live: updateVisiblePoints only writes to labels the grid has
        // registered, so it cannot reach a station an import just created; repaintLayout rebuilds the grid and then
        // updates, in one event.
        javax.swing.SwingUtilities.invokeLater(() -> ui.repaintLayout());
    }

    /** Loads the active configuration: the startup resume, the same as choosing what was chosen last time. */
    public void loadActive()
    {
        String active = session().getStore().getActiveConfiguration();

        // Quietly: a modal before the window is up would tell a user who asked for nothing about something unknown; a
        // failed resume goes to the log and leaves its reasons in the list.
        if (active != null) load(active, false);
    }

    /**
     * Says why, and answers true, where something other than autonomy holds the setup
     * (`TrainControlUI.refusedWhileSomethingElseRuns`): the question every door a user loads a configuration through
     * asks first.  While autonomy itself is busy the load keeps its own offer to stop the trains
     * (`prepareAutonomyReload`).
     *
     * @return true when the load is refused
     */
    public boolean refusedWhileRunning()
    {
        return ui.refusedWhileSomethingElseRuns();
    }

    /**
     * A load the user asked for (enable button, Autonomy menu, diagram banner): refused while something other than
     * autonomy holds the setup.  The application's own reloads at rest call `load` directly.
     *
     * @param name the configuration to load
     */
    public void loadByRequest(String name)
    {
        if (refusedWhileRunning()) return;

        load(name, true);
    }

    /**
     * @param name the configuration to load
     * @param interactive whether the user asked for this, and so should be told when it fails
     */
    public void load(String name, boolean interactive)
    {
        load(name, interactive, true);
    }

    /**
     * The same, told whether the running layout's state is worth keeping.  Loading normally captures first (placements,
     * homes and facings set while the outgoing configuration ran go back into it), which is wrong when the setup is
     * newer: a rebuild asked for because the setup just changed would have the stale capture overwrite the edit.
     *
     * @param name the configuration to load
     * @param interactive whether the user asked for this, and so should be told when it fails
     * @param captureRunningState whether to fold the running layout's state back in first
     */
    public void load(String name, boolean interactive, boolean captureRunningState)
    {
        // The same gate the JSON path applies before replacing the layout: confirm, then stop whatever
        // is moving.  Owned by the main window because stopping trains is its business, not a panel's.
        if (!ui.prepareAutonomyReload())
        {
            refresh();
            return;
        }

        // WHILE A SETUP EDIT A RUN DECLINED WAITS FOR ITS REBUILD, THE TRAINS ARE CARRIED ACROSS RATHER THAN FOLDED.
        // The running layout is the older of the two, so folding would take the edit away; but where its trains stand
        // is the railway's to say (OB-183), so these loads carry them (`carryTheTrainsAcross`).  A layout that holds a
        // path is folded as any other (`loadPrepared`), since carrying it lost the Auto tab's settings and a captured
        // timetable.
        if (captureRunningState && ui.isSetupNewerThanTheRunningLayout())
        {
            // The configuration running, or the one a reset forgot the name of: Unload, a deleted setup and a railway
            // switch forget it too, and "nothing running" would carry the previous railway's trains across.
            final String running = ui.getActiveDiagramConfiguration() != null ? ui.getActiveDiagramConfiguration()
                : ui.getConfigurationTheResetForgot();

            if (running != null && running.equals(name))
            {
                ui.carryTheTrainsAcross(() -> loadPrepared(name, interactive, false));

                refresh();
                return;
            }

            // The one running goes first, with the edit and where the trains stand, so it is folded below as any is.
            if (running != null)
            {
                // Quietly: its failure is said below, in words about this choice rather than a start-up
                final boolean carried = ui.carryTheTrainsAcross(() -> loadPrepared(running, false, false, false));

                // NOT WHILE THE ONE RUNNING CANNOT BE USED WITH THE EDIT THAT WAITS: refused, it stays loaded (with its
                // errors where it builds with them, so they can be fixed), the edit is kept, and what stops it is in
                // the list.
                if (!carried && ui.isSetupNewerThanTheRunningLayout())
                {
                    int things = Math.max(countBlocking(), session().errorCount());

                    String why = I18n.f("autosetup.ui.errorCannotKeepTrainsBeforeChoosing", running, name);

                    if (things > 0)
                    {
                        why += "\n\n" + (things == 1 ? I18n.t("autosetup.ui.errorCannotBuildDetailOne")
                            : I18n.f("autosetup.ui.errorCannotBuildDetail", things));
                    }

                    refresh();

                    if (interactive) JOptionPane.showMessageDialog(ui, why);
                    else if (ui.getModel() != null) ui.getModel().log(why);

                    return;
                }
            }
        }

        // AND THE TURNS THE RAILWAY OWES, across a fold of the configuration running: a turn made at a destination is
        // written into the setup only while the railway is idle, and the fold writes where each train stands, not which
        // way.
        final String runningNow = ui.getActiveDiagramConfiguration();

        if (captureRunningState && runningNow != null && runningNow.equals(name))
        {
            ui.keepThePendingTurnsAcross(() -> loadPrepared(name, interactive, captureRunningState));
        }
        else
        {
            // ANOTHER CONFIGURATION: the turns the railway owes are the one running's, written into it before its fold
            if (captureRunningState && runningNow != null) ui.writeTheTurnsOwed();

            loadPrepared(name, interactive, captureRunningState);
        }
    }

    /**
     * The load itself, once `prepareAutonomyReload` has been asked: the capture where it is wanted, the rebuild, and what
     * follows it.
     *
     * @param name the configuration to load
     * @param interactive whether the user asked for this, and so should be told when it fails
     * @param captureRunningState whether to fold the running layout's state back in first
     */
    private void loadPrepared(String name, boolean interactive, boolean captureRunningState)
    {
        loadPrepared(name, interactive, captureRunningState, true);
    }

    /**
     * The same, told whether a load that is not interactive says in the log that it failed - not where its caller says
     * so itself, in words about what was asked for.
     *
     * @param name the configuration to load
     * @param interactive whether the user asked for this, and so should be told when it fails
     * @param captureRunningState whether to fold the running layout's state back in first
     * @param logAFailure whether a failure that is not interactive goes to the log as a start-up's would
     */
    private void loadPrepared(String name, boolean interactive, boolean captureRunningState, boolean logAFailure)
    {
        // What was set while the outgoing configuration ran goes back into THAT configuration, by name, before anything
        // is replaced: store-active and what-is-running can disagree after a refused load.
        //
        // NOT WHILE A SETUP EDIT A RUN DECLINED WAITS FOR ITS REBUILD: folding the older running layout would take the
        // edit away.
        //
        // AND A LAYOUT HOLDING A PATH WITH EACH TRAIN UNDER WAY ON ONE POINT: a locked path holds every point on it for
        // its train, and folded point by point the train stood on each, which the configuration refuses as one
        // locomotive in two places.  Written at the point it is kept at, it stands once and the rest of the fold is
        // kept.
        if (captureRunningState && !ui.isSetupNewerThanTheRunningLayout() && ui.getActiveDiagramConfiguration() != null
            && ui.getModel() != null && ui.getModel().hasAutoLayout() && ui.getModel().getAutoLayout().isValid())
        {
            try
            {
                org.traincontrol.automation.Layout folding = ui.getModel().getAutoLayout();

                // AND ITS SETTINGS AS BUILT FOLLOW (OB-343): a build the load then declines leaves this railway running
                session().captureFromLayout(folding.toJSON(folding.getLastPointsReached()),
                    ui.getActiveDiagramConfiguration(), ui.settingsAsBuiltFor(folding));
            }
            catch (Exception e)
            {
                // capture is a courtesy; failing to capture must not block loading
                if (ui.getModel().isDebug()) ui.getModel().log(String.valueOf(e.getMessage()));
            }
        }

        // remembered so a failed load can put the store back: what loads on the next start must be
        // something that actually loaded, not something that was refused partway
        String previous = session().getStore().getActiveConfiguration();

        session().getStore().setActiveConfiguration(name);

        // AND NO LOCOMOTIVE THE DATABASE DOES NOT HOLD (OB-349), before the build: the loader would drop each from the
        // railway and leave it in the setup.  Said in the log and, as an import's is, in a dialog once the window is
        // up; what it removed is gone from the setup, so a later load finds nothing to say again.
        adviseOfLocomotivesLeftOut(name, forgetLocomotivesNotInTheDatabase(name));

        session().rebuild();

        if (session().hasBlockingProblems())
        {
            // refreshed FIRST, so the reasons are on screen behind the message that refers to them
            refresh();

            if (interactive)
            {
                // One of these is far and away the commonest case, and "1 things" in the one message
                // a user meets before their railway will run is not the first impression to make.
                int blocking = countBlocking();

                JOptionPane.showMessageDialog(ui, blocking == 1
                    ? I18n.t("autosetup.ui.errorCannotBuildDetailOne")
                    : I18n.f("autosetup.ui.errorCannotBuildDetail", blocking));
            }
            else if (logAFailure && ui.getModel() != null)
            {
                ui.getModel().log(I18n.f("autosetup.ui.infoResumeFailed", name));
            }

            revert(previous);
            refresh();
            return;
        }

        if (ui.getModel() == null)
        {
            revert(previous);
            return;
        }

        try
        {
            ui.getModel().parseAuto(session().buildConfiguration());

            // ITS SETTINGS AS BUILT, for the folds to tell what the railway changed since
            ui.noteTheRailwaysSettingsAsBuilt();

            // THE RULE FOR EVERY PARSE: a setup that turns atomic routes off while track autonomy runs over has no
            // length comes up atomic, with the reason in the log.  This is the only door into `parseAuto` (OB-254).
            ui.keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack();

            // remembered for next start, the way loading has always doubled as choosing
            save();

            // everything that follows success - dependent tabs, the monitor, the overlay toggle, the
            // jump to the diagram - in one place, shared with the startup resume
            ui.autonomyLoadedFromDiagram(name, !interactive);
        }
        catch (RuntimeException e)
        {
            if (interactive) JOptionPane.showMessageDialog(ui, String.valueOf(e.getMessage()));
            else if (ui.getModel() != null) ui.getModel().log(String.valueOf(e.getMessage()));

            revert(previous);
        }

        refresh();
    }

    /**
     * The session's own count.
     *
     * @return how many problems would stop the setup being built
     */
    private int countBlocking()
    {
        return session().blockingProblemCount();
    }

    private void revert(String previous)
    {
        session().getStore().setActiveConfiguration(previous);
        session().rebuild();
    }

    /**
     * Sets autonomy up for a layout that has none: one configuration, named by the user, that everything else here
     * applies to.
     */
    public void initialize()
    {
        // NOT WHILE AUTONOMY RUNS: it writes a configuration, and may tick pages out, under the railway
        if (ui.refuseWhileARunHoldsTheSetup(ui)) return;

        String name = (String) JOptionPane.showInputDialog(ui,
            I18n.t("autosetup.ui.promptConfigurationName"),
            I18n.t("autosetup.ui.menuInitialize"),
            JOptionPane.PLAIN_MESSAGE, null, null, suggestedConfigurationName());

        if (name == null || name.trim().isEmpty()) return;

        try
        {
            session().initialize(name.trim());
        }
        catch (IOException e)
        {
            // Only a name collision is reported as one: initialize() also SAVES, and a full disk was announced as "in
            // use".
            JOptionPane.showMessageDialog(ui, nameRefused(e, name, String.valueOf(e.getMessage())));
            return;
        }

        // BEFORE anything is derived or loaded, so the pages that cannot be told apart are out when the first graph is
        // built.
        List<String> shut = session().excludeRepeatedSensorPages();

        if (!shut.isEmpty())
        {
            save();

            JOptionPane.showMessageDialog(ui, I18n.f("autosetup.ui.infoPagesExcludedForSensors",
                shut.size(), String.join(", ", shut)));
        }

        refresh();

        // And run it, through load(): the same guarded path the chooser uses, so trains are stopped and the user asked
        // first.
        load(name.trim(), true);

        // A refused load must still leave the new configuration CHOSEN: load() puts the store back to what was active
        // before (nothing here), and the editor is gated on a chosen configuration, so the errors it exists to fix
        // would lock its door.
        if (ui.getActiveDiagramConfiguration() == null)
        {
            session().getStore().setActiveConfiguration(name.trim());
            session().rebuild();

            refresh();
        }

        // Then show them what they made autonomy for: the stations appear on the track diagram, whose top strip says
        // what needs looking at.
        ui.showLayoutTab();
    }

    /**
     * A name the user can simply accept ("Autonomy 1", renamed later from the same menu): the next FREE number, not the
     * count plus one, which would suggest a name in use when "Autonomy 1" and "Autonomy 3" exist.
     */
    private String suggestedConfigurationName()
    {
        java.util.List<String> taken = session().getStore().getConfigurationNames();

        for (int next = 1; next < 1000; next++)
        {
            String candidate = I18n.f("autosetup.ui.defaultConfigurationName", next);

            if (!taken.contains(candidate)) return candidate;
        }

        return I18n.f("autosetup.ui.defaultConfigurationName", taken.size() + 1);
    }

    /**
     * A file chooser opened where the last one was, like every other in the application; the working directory is
     * wherever a packaged build's shortcut points.
     */
    private javax.swing.JFileChooser chooser()
    {
        return new QuickFileChooser(TrainControlUI.getPrefs()
            .get(TrainControlUI.LAST_USED_FOLDER, new java.io.File(".").getAbsolutePath()));
    }

    /**
     * Remembers the folder a file was chosen in, so the next chooser opens there.
     *
     * @param file the file that was chosen
     */
    private void rememberFolder(java.io.File file)
    {
        if (file == null || file.getParent() == null) return;

        TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, file.getParent());
    }

    /**
     * Reads an exported configuration file in as a new named configuration.  The file carries the configuration AND the
     * track decisions it refers to (station names, which squares are stations, lengths, directions, portals, captions),
     * which live only in the setup.  The shared half is merged, never adopted wholesale, so an import cannot rename a
     * station named here.
     */
    public void importConfiguration()
    {
        // NOT WHILE AUTONOMY RUNS, as Delete beside it is refused, and before anything is asked: an import writes the
        // setup's shared half (a station's name among it) into any configuration, and the fold after a declined reload
        // read the running railway through names it had changed.
        if (ui.refuseWhileARunHoldsTheSetup(ui)) return;

        javax.swing.JFileChooser chooser = chooser();

        if (chooser.showOpenDialog(ui) != javax.swing.JFileChooser.APPROVE_OPTION) return;

        rememberFolder(chooser.getSelectedFile());

        String name = JOptionPane.showInputDialog(ui, I18n.t("autosetup.ui.promptImportName"),
            chooser.getSelectedFile().getName().replaceAll("\\.json$", ""));

        if (name == null || name.trim().isEmpty()) return;

        // READ BEFORE THE QUESTION BELOW, so that it can say what Yes does: a bundle replaces the configuration of that
        // name, an old autonomy.json fills in what that configuration does not already say (MT-298).
        org.json.JSONObject file;

        AutonomySession.ImportFormat format;

        try
        {
            byte[] bytes = java.nio.file.Files.readAllBytes(chooser.getSelectedFile().toPath());

            file = new org.json.JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));

            // Told apart by shape, not by where it came from or what it is called: a user has one Import.
            format = AutonomySession.detectImportFormat(file);
        }
        catch (IOException | RuntimeException e)
        {
            JOptionPane.showMessageDialog(ui,
                I18n.f("autosetup.ui.errorImportUnreadable", String.valueOf(e.getMessage())));

            refresh();
            return;
        }

        if (format == AutonomySession.ImportFormat.UNKNOWN)
        {
            JOptionPane.showMessageDialog(ui, I18n.t("autosetup.ui.errorImportUnrecognised"));
            return;
        }

        // NOTHING THAT REPLACES THE CONFIGURATION IN USE: a bundle, or a bare-shape file (the setup folder's own, an
        // export from before bundles), is imported into it, and the reload that follows captured the running railway
        // straight back over it, so "Replace it?" answered Yes did not replace, and their placements are not where the
        // running trains stand (OB-183).  So they go in under another name and are run by choosing them.  An old file
        // is not refused: it fills gaps (MT-298) and places no train on the railway running.
        String inUse = ui.getActiveDiagramConfiguration();

        final boolean intoTheOneInUse = inUse != null && inUse.equals(name.trim());

        if (intoTheOneInUse && format != AutonomySession.ImportFormat.LEGACY_GRAPH)
        {
            JOptionPane.showMessageDialog(ui, I18n.f("autosetup.ui.errorImportIntoConfigurationInUse", inUse,
                I18n.t("autosetup.ui.menuAutonomy") + " > " + I18n.f("autosetup.ui.menuConfigurations", inUse)));

            return;
        }

        // importing over an existing name replaces it, or fills its gaps, which is sometimes wanted and never silent - and
        // into the configuration in use an old file places none of its trains, which its question says
        if (session().getStore().getConfigurationNames().contains(name.trim()))
        {
            String question = format != AutonomySession.ImportFormat.LEGACY_GRAPH ? "autosetup.ui.confirmImportOverwrites"
                : intoTheOneInUse ? "autosetup.ui.confirmImportFillsGapsInUse" : "autosetup.ui.confirmImportFillsGaps";

            // What counts as not set (a default, a home taken off, an emptied list) is Automation.md's, not the question's.
            int replace = JOptionPane.showOptionDialog(ui, I18n.f(question, name.trim()),
                I18n.t("autosetup.ui.title"), JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE, null,
                TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]);

            if (replace != 0) return;
        }

        try
        {
            if (format == AutonomySession.ImportFormat.LEGACY_GRAPH)
            {
                // Into the configuration just named: a layout that has never had autonomy has no active configuration,
                // so the placements, homes and termini would have nowhere to land and the load would be asked for null
                // (OB-106).
                importLegacyGraph(file, name.trim());
                return;
            }

            int filled = session().importBundle(name.trim(), file);

            // AND THE SQUARES IT BROUGHT TO THEIR ARROWS, said as opening a setup says them (OB-351)
            sayWhatWasBroughtToItsArrows();

            // AND WHICH PAGES WERE LEFT OUT, said before the save: an export describes the railway it came from, and a
            // page the setup knows about and cannot load stops every later save from tidying anything.
            java.util.List<String> notHere = session().getPagesLeftOutOfLastImport();

            if (!notHere.isEmpty())
            {
                JOptionPane.showMessageDialog(ui,
                    I18n.f("autosetup.ui.warnImportPagesNotHere", String.join(", ", notHere)),
                    I18n.t("autosetup.ui.titleImportPagesNotHere"), JOptionPane.WARNING_MESSAGE);
            }

            // AND THE LOCOMOTIVES THIS DATABASE DOES NOT HOLD, out of it before the save (OB-349): the configuration
            // imported is loaded only where nothing else runs, so the load cannot be left to do it
            java.util.List<AutonomySession.LocomotiveLeftOut> leftOut = forgetLocomotivesNotInTheDatabase(name.trim());

            save();

            // Said out loud, so nobody wonders whether their station names came across; a file written before exports
            // carried the track decisions fills nothing, and that is worth knowing too.
            JOptionPane.showMessageDialog(ui, I18n.f("autosetup.ui.infoImported", filled));

            showLocomotivesLeftOut(name.trim(), leftOut);

            loadAfterImport(name.trim());
        }
        catch (IOException | RuntimeException e)
        {
            // A COLLISION IS NOT AN UNREADABLE FILE: the store reports its refusals as message keys, and the one an
            // import can hit is a name sharing another configuration's file; say that.
            JOptionPane.showMessageDialog(ui, nameRefused(e, name,
                I18n.f("autosetup.ui.errorImportUnreadable", String.valueOf(e.getMessage()))));
        }

        refresh();
    }

    /**
     * Logs the squares a door brought to their arrows since they were last said, once each (OB-351).
     */
    private void sayWhatWasBroughtToItsArrows()
    {
        for (String line : session().takeSquaresBroughtToTheirArrows())
        {
            if (ui.getModel() != null) ui.getModel().log(line);
        }
    }

    /**
     * What to tell somebody whose configuration name the store refused, or `otherwise` for any other failure.  The
     * store reports its refusals as message keys, translated here: a name another configuration already HAS, and a name
     * that would be saved in another configuration's FILE ("Night_ Yard" beside "Night: Yard").  One method for the
     * five doors that take a name.
     *
     * @param e what was thrown
     * @param name the name that was typed, or null
     * @param otherwise what to say for anything else
     * @return the sentence
     */
    private static String nameRefused(Exception e, String name, String otherwise)
    {
        String typed = name == null ? "" : name.trim();

        if (e instanceof AutonomyCompanionStore.NameSharesAFile)
        {
            return I18n.f("autosetup.ui.errorNameSharesAFile", typed,
                ((AutonomyCompanionStore.NameSharesAFile) e).getSharedWith());
        }

        return AutonomyCompanionStore.ERROR_NAME_IN_USE.equals(e.getMessage())
            ? I18n.f("autosetup.ui.errorNameInUse", typed) : otherwise;
    }

    /** A blank line between two paragraphs of a dialog: "\n", which a text area breaks at, not the system's ending. */
    private static final String NEWLINES = "\n\n";

    /**
     * Chooses the configuration named at the Import prompt, creating it where there is none of that name: the
     * configuration an old autonomy.json's per-point half is written into through `setPointProperty`.
     * `setActiveConfiguration` moves the pointer and nothing else, and the door moves it back to the running
     * configuration before anything is saved.
     *
     * @param store the setup's store
     * @param typed the name typed at the prompt, or null or blank for none
     * @param suggested the name to use where none was typed
     * @return the configuration now chosen
     * @throws java.io.IOException where a configuration of that name cannot be created
     */
    static String activateTheConfigurationNamed(AutonomyCompanionStore store, String typed, String suggested)
        throws java.io.IOException
    {
        String name = typed == null || typed.trim().isEmpty() ? suggested : typed.trim();

        if (!store.getConfigurationNames().contains(name)) store.createConfiguration(name, null);

        store.setActiveConfiguration(name);

        return name;
    }

    /**
     * A list for the log as one entry: its heading, and each line under it, indented.  One entry, because the log puts
     * its newest entry on top and a heading logged on its own would sit below its lines.
     *
     * @param heading what the list is
     * @param lines its lines
     * @return the entry
     */
    private static String listForTheLog(String heading, java.util.List<String> lines)
    {
        StringBuilder entry = new StringBuilder(heading);

        for (String line : lines) entry.append("\n  ").append(line);

        return entry.toString();
    }

    /**
     * Takes the locomotives the database does not hold out of a configuration, and says so in the log (OB-349).
     *
     * @param configuration the configuration's name
     * @return what was let go of, for an import to show as well
     */
    private java.util.List<AutonomySession.LocomotiveLeftOut> forgetLocomotivesNotInTheDatabase(String configuration)
    {
        if (ui.getModel() == null) return java.util.Collections.emptyList();

        java.util.List<AutonomySession.LocomotiveLeftOut> leftOut = session().forgetLocomotivesNotIn(configuration,
            new java.util.HashSet<>(ui.getModel().getLocList()));

        logLocomotivesLeftOut(configuration, leftOut);

        return leftOut;
    }

    /**
     * The locomotives a configuration was left without, as one entry in the log: one line for each, saying what it lost
     * where (OB-349).
     *
     * @param configuration the configuration's name
     * @param leftOut what was let go of
     */
    private void logLocomotivesLeftOut(String configuration, java.util.List<AutonomySession.LocomotiveLeftOut> leftOut)
    {
        if (leftOut.isEmpty() || ui.getModel() == null) return;

        ui.getModel().log(listForTheLog(I18n.f("autosetup.ui.locomotivesLeftOut", configuration), linesFor(leftOut)));
    }

    /**
     * The same list in a dialog, for an import.  It scrolls past ten lines, as `TailCrossedPrompt`'s does, so the
     * dialog stays the same height however many there are.
     *
     * @param configuration the configuration's name
     * @param leftOut what was let go of; nothing is shown when it is empty
     */
    private void showLocomotivesLeftOut(String configuration, java.util.List<AutonomySession.LocomotiveLeftOut> leftOut)
    {
        if (leftOut.isEmpty()) return;

        showLocomotivesLeftOutLines(configuration, linesFor(leftOut));
    }

    private void showLocomotivesLeftOutLines(String configuration, java.util.List<String> lines)
    {
        JOptionPane.showMessageDialog(ui,
            DetailsArea.message(I18n.f("autosetup.ui.locomotivesLeftOut", configuration), lines),
            I18n.t("autosetup.ui.titleLocomotivesLeftOut"), JOptionPane.WARNING_MESSAGE);
    }

    /**
     * The same dialog for a load that took something out (OB-349).  The lines are made now, from the setup as it is,
     * and the dialog waits for the window if it is still connecting.
     *
     * @param configuration the configuration's name
     * @param leftOut what was let go of; nothing is shown when it is empty
     */
    private void adviseOfLocomotivesLeftOut(String configuration, java.util.List<AutonomySession.LocomotiveLeftOut> leftOut)
    {
        if (leftOut.isEmpty()) return;

        final java.util.List<String> lines = linesFor(leftOut);

        ui.whenTheConnectingNoticeIsGone(() -> showLocomotivesLeftOutLines(configuration, lines));
    }

    /**
     * One line for each locomotive, in name order: its name, and what it lost where.
     *
     * @param leftOut what was let go of
     * @return the lines
     */
    private java.util.List<String> linesFor(java.util.List<AutonomySession.LocomotiveLeftOut> leftOut)
    {
        java.util.Map<String, java.util.List<String>> lost = new java.util.TreeMap<>();

        for (AutonomySession.LocomotiveLeftOut one : leftOut)
        {
            String where = session().describeTile(one.square);

            String what;

            switch (one.what)
            {
                case STANDING: what = I18n.f("autosetup.ui.leftOutStanding", where); break;
                case HOME: what = I18n.f("autosetup.ui.leftOutHome", where); break;
                case ALLOWED: what = I18n.f("autosetup.ui.leftOutAllowed", where); break;
                default: what = I18n.f("autosetup.ui.leftOutExcluded", where); break;
            }

            java.util.List<String> its = lost.computeIfAbsent(one.locomotive, name -> new java.util.ArrayList<>());

            if (!its.contains(what)) its.add(what);
        }

        java.util.List<String> lines = new java.util.ArrayList<>();

        for (java.util.Map.Entry<String, java.util.List<String>> one : lost.entrySet())
        {
            lines.add(I18n.f("autosetup.ui.leftOutLine", one.getKey(), String.join(", ", one.getValue())));
        }

        return lines;
    }

    /**
     * The import dialog's sentence for the old points whose names were not kept, in the form the number takes ("1 old
     * point").
     */
    private static String sharedSquaresSentence(int points)
    {
        return I18n.f("autosetup.ui.importSharedSquares" + I18n.countForm(points), points);
    }

    /** The import dialog's sentence for what it deliberately left behind, in the form the number takes ("1 thing"). */
    private static String leftBehindSentence(int things)
    {
        return I18n.f("autosetup.ui.leftBehind" + I18n.countForm(things), things);
    }

    /**
     * Reads an old autonomy.json onto the squares carrying the same sensors; the one Import action has already parsed
     * it.
     *
     * @param file the parsed autonomy.json
     */
    private void importLegacyGraph(org.json.JSONObject file, String name)
    {
        // First, because an s88 on two squares stops an import deciding anything (those points are refused and listed):
        // shut the repeats before reading the file.  Here as well as at setup creation, since an older setup, or one
        // whose pages were redrawn, never had it applied.
        //
        // AND BEFORE THE SNAPSHOT BELOW (OB-302): an exclusion is the setup's shared half, announced at once, so a
        // later failure of the import is no reason to take it back.
        List<String> shut = session().excludeRepeatedSensorPages();

        if (!shut.isEmpty())
        {
            JOptionPane.showMessageDialog(ui, I18n.f("autosetup.ui.infoPagesExcludedForSensors",
                shut.size(), String.join(", ", shut)));
        }

        // EVERYTHING AS IT WAS, for an import that fails part way: the configuration is created and chosen before the
        // file is read and importLegacy writes as it reads, so the failure must put all of that back, as the bundle
        // import does.
        org.json.JSONObject was = session().snapshotSetup();

        // AND THE RECORD OF HOW THE RAILWAY WAS BUILT WITH IT (OB-343): the fold below updates it, and the setup put
        // back must not keep what the fold took
        final TrainControlUI.SettingsAsBuiltNote railwaySettings = ui.noteSettingsAsBuilt();

        // Somewhere for the import to land, before it starts (OB-106): the per-point half (placements, homes, termini,
        // facings) belongs to a configuration, and importLegacy writes it through setPointProperty, which addresses the
        // ACTIVE one; with none, that half was dropped silently.
        //
        // INTO THE CONFIGURATION NAMED AT THE PROMPT, not the one in use: the name the door just asked for, and warned
        // about replacing, must not be thrown away.  The one named is created where it does not exist and chosen while
        // the import writes into it; the configuration running is chosen again before anything is saved (below), others
        // are left as they were.
        String into;

        try
        {
            // suggestedConfigurationName, not the bundle key directly: that value is "Autonomy {0}" and asking for it
            // with I18n.t would put the placeholder on screen as the configuration's NAME.
            into = activateTheConfigurationNamed(session().getStore(), name, suggestedConfigurationName());
        }
        catch (java.io.IOException e)
        {
            // A NAME IN USE IS NOT AN UNREADABLE FILE: a configuration file of that name already sits beside
            // the setup, under another name that shares its file.
            JOptionPane.showMessageDialog(ui, nameRefused(e, name,
                I18n.f("autosetup.ui.errorImportUnreadable", String.valueOf(e.getMessage()))));

            return;
        }

        // Whether the import reached the save, after which a failure is not rolled back: what was saved stays.
        boolean saved = false;

        try
        {
            // The names this database actually holds, so a placement naming something else is
            // refused here rather than by the whole layout failing to build later
            java.util.Set<String> known = ui.getModel() == null
                ? null : new java.util.LinkedHashSet<>(ui.getModel().getLocList());

            // INTO THE CONFIGURATION RUNNING, BY ITS NAME: what the running layout knows goes into it FIRST, and the
            // reload after the import does not capture again, as that wrote the running railway back over the homes and
            // settings the import brought.  The file places no train in it: where a train stands on the running railway
            // is the railway's to say (OB-183).  While autonomy runs, the door refuses the name.  Nor while a setup
            // edit a run declined waits for its rebuild: folding the older running layout would take that edit away,
            // and the reload does not fold either (`load`).
            final boolean intoTheOneRunning = into.equals(ui.getActiveDiagramConfiguration());

            boolean captured = false;

            if (intoTheOneRunning && !ui.isAutonomyBusy() && !ui.isSetupNewerThanTheRunningLayout()
                && ui.getModel() != null && ui.getModel().hasAutoLayout() && ui.getModel().getAutoLayout().isValid())
            {
                try
                {
                    session().captureFromLayout(ui.getModel().getAutoLayout().toJSON(), into,
                        ui.settingsAsBuiltFor(ui.getModel().getAutoLayout()));

                    captured = true;
                }
                catch (Exception e)
                {
                    // as load() treats it: a courtesy, and failing it leaves the reload to capture, as before
                    if (ui.getModel().isDebug()) ui.getModel().log(String.valueOf(e.getMessage()));
                }
            }

            AutonomySession.LegacyImport result = session().importLegacy(file, known, !intoTheOneRunning);

            // Read to its end before anything is saved, so a file that throws here has changed nothing.  This
            // came after the save, and it reads the timetable and the edges strictly.
            java.util.List<String> left = session().whatALegacyImportLeaves(file);

            // THE LOCOMOTIVES THIS DATABASE DOES NOT HOLD (OB-349): the trains the file stands that were not placed, and
            // whatever else the configuration now names of them - a home, a name on a list - taken out of it, in one list
            java.util.List<AutonomySession.LocomotiveLeftOut> leftOut = new java.util.ArrayList<>(result.unknownPlacements);

            if (known != null) leftOut.addAll(session().forgetLocomotivesNotIn(into, known));

            // AND THE CONFIGURATION RUNNING CHOSEN AGAIN, BEFORE THE SAVE: the save writes the imported one as the
            // configuration the next start resumes, so "No" to stopping the trains, or a refused reload, would leave
            // that start loading it.
            String inUse = AutonomySession.configurationToLoadAfterImport(ui.getActiveDiagramConfiguration(), into);

            if (inUse != null) session().getStore().setActiveConfiguration(inUse);

            saved = true;

            save();

            // LOAD AUTONOMY IS LEFT AS IT WAS: since OB-254 it resumes the active setup, which the import has just
            // made.

            // The sentence counts; what it could not do goes under it in a Details list, and to the log.
            java.util.List<String> details = new java.util.ArrayList<>();

            if (!result.unmatched.isEmpty()) details.add(String.join(", ", result.unmatched));

            // The ones this database does not hold are not named here: they have a list of their own, below (OB-349)
            if (!result.duplicateLocomotives.isEmpty())
            {
                details.add(I18n.f("autosetup.ui.infoLegacyDuplicateLocs",
                    String.join(", ", result.duplicateLocomotives)));
            }

            // And the ones this configuration already has standing somewhere, which stay there
            if (!result.alreadyPlaced.isEmpty())
            {
                details.add(I18n.f("autosetup.ui.infoLegacyAlreadyPlaced", String.join(", ", result.alreadyPlaced)));
            }

            // And the homes it already has, which stay too
            if (!result.homesKept.isEmpty())
            {
                details.add(I18n.f("autosetup.ui.infoLegacyHomesKept", String.join(", ", result.homesKept)));
            }

            // AND WHAT IT LEFT ON A SQUARE ALREADY TAKEN, and the homes it named twice (OB-304): each a choice the
            // import made for the operator.
            if (!result.squareTaken.isEmpty())
            {
                details.add(I18n.f("autosetup.ui.infoLegacySquareTaken", String.join(", ", result.squareTaken)));
            }

            if (!result.homeSquareTaken.isEmpty())
            {
                details.add(I18n.f("autosetup.ui.infoLegacyHomeSquareTaken",
                    String.join(", ", result.homeSquareTaken)));
            }

            if (result.duplicateHomes > 0)
            {
                details.add(I18n.f("autosetup.ui.infoLegacyDuplicateHomes", result.duplicateHomes));
            }

            // And the trains it did not place, into the configuration running
            if (!result.notPlacedInUse.isEmpty())
            {
                details.add(I18n.f("autosetup.ui.infoLegacyNotPlacedInUse",
                    String.join(", ", result.notPlacedInUse), into,
                    // THE DOOR THAT PUTS DOWN A TRAIN THE DIAGRAM DOES NOT HAVE: "Place <train>", for the train
                    // chosen in the main window - "Place Locomotive..." lists only the trains already standing
                    I18n.f("layout.ui.menuPlaceLocomotive", String.valueOf((char) 0x2026))));
            }

            // AND WHAT IT DID NOT BRING (MT-257 item 3): four things are dropped on purpose (per-edge accessory
            // commands, edge lengths, the timetable, route activations), each for a reason at
            // `AutonomySession.importLegacy`.  The list goes to the log because it is a list and this is a dialog,
            // which says it is there.  Read above, before the save.
            if (!left.isEmpty() && ui.getModel() != null)
            {
                ui.getModel().log(listForTheLog(I18n.t("autosetup.ui.leftBehindHeading"), left));
            }

            // AND THE FACINGS THAT WERE GUESSED: the old format states which way a train faces only through its one-way
            // edges, so where they cannot say, a placement landing on a split square has one chosen for it.  In the log
            // rather than the dialog: something to read once and then check on the diagram.
            if (result.facingsInvented > 0 && ui.getModel() != null)
            {
                ui.getModel().log(I18n.f("autosetup.ui.facingsGuessed" + I18n.countForm(result.facingsInvented),
                    result.facingsInvented));
            }

            // AND THE ONES THE FILE STATES AND THIS DIAGRAM CANNOT HOLD, by name: stood the only way their
            // square allows, which is the other way round from how they drive.
            if (!result.facingsNotHeld.isEmpty() && ui.getModel() != null)
            {
                ui.getModel().log(I18n.f("autosetup.ui.facingsNotHeld", String.join(", ", result.facingsNotHeld)));
            }

            // AND THE OLD FILE'S ONE-WAY RUNNING, carried onto the diagram - or, onto one whose directions are set,
            // counted and left alone.
            if (!result.directionsCarried.isEmpty() && ui.getModel() != null)
            {
                ui.getModel().log(I18n.f("autosetup.ui.directionsCarried", result.directionsCarried.size()));
            }

            if (result.directionsNotCarried > 0 && ui.getModel() != null)
            {
                ui.getModel().log(I18n.f("autosetup.ui.directionsNotCarried", result.directionsNotCarried));
            }

            // AND WHERE IT WENT: into the configuration named, which is the one in use only where nothing else
            // was running - otherwise the dialog counted trains the diagram does not show, and named no configuration.
            String where = inUse == null || into.equals(inUse)
                ? I18n.f("autosetup.ui.infoLegacyImportedInto", into)
                : I18n.f("autosetup.ui.infoLegacyImportedNotInUse", into, inUse,
                    I18n.t("autosetup.ui.menuAutonomy") + " > " + I18n.f("autosetup.ui.menuConfigurations", inUse));

            // AND THE OLD POINTS THAT SHARED A SQUARE, whose names were not kept - a list, so to the log
            if (!result.namesNotKept.isEmpty() && ui.getModel() != null)
            {
                ui.getModel().log(listForTheLog(I18n.t("autosetup.ui.importSharedSquaresHeading"), result.namesNotKept));
            }

            if (!result.namesNotKept.isEmpty()) details.add(sharedSquaresSentence(result.namesNotKept.size()));

            if (!left.isEmpty()) details.add(leftBehindSentence(left.size()));

            String counted = I18n.f("autosetup.ui.infoLegacyImported", result.matched, result.placed, result.reversing,
                result.settings, result.skipped, result.unmatched.size()) + NEWLINES + where;

            if (!details.isEmpty() && ui.getModel() != null) ui.getModel().log(listForTheLog(counted, details));

            JOptionPane.showMessageDialog(ui, details.isEmpty() ? counted : DetailsArea.message(counted, details));

            logLocomotivesLeftOut(into, leftOut);
            showLocomotivesLeftOut(into, leftOut);

            // The one just created when nothing was running - so the import is loaded rather than left sitting on disk -
            // and otherwise the one running, loaded again over the shared half the import merged.
            loadAfterImport(into, !captured);
        }
        // RuntimeException alone: the Import action decides what a file IS, so nothing in here is checked; a malformed
        // file lands as a JSONException.
        catch (RuntimeException e)
        {
            // Put back, where nothing has been saved yet
            if (!saved)
            {
                session().getStore().restoreSetup(was);
                session().rebuild();

                ui.putSettingsAsBuiltBack(railwaySettings);
            }

            JOptionPane.showMessageDialog(ui,
                I18n.f("autosetup.ui.errorImportUnreadable", String.valueOf(e.getMessage())));
        }

        refresh();
    }

    /**
     * Writes the active configuration out where the user chooses, for another machine to import.
     */
    public void exportConfiguration()
    {
        // NOT WHILE A RUN HOLDS THE SETUP, as Import is not (MT-601), and before anything is asked: the export folds
        // the running railway into the setup first
        if (ui.refuseUntilTheTrainsStop(ui)) return;

        String name = selected();

        if (name == null) return;

        // WHERE THE TRAINS ARE, folded into the configuration running first, as New Configuration does: its file
        // holds where they stood before the last run, and the pace and the timetable as at the load
        ui.captureRunningLayout();

        org.json.JSONObject configuration = session().getStore().exportBundle(name);

        if (configuration == null) return;

        javax.swing.JFileChooser chooser = chooser();
        chooser.setSelectedFile(new java.io.File(chooser.getCurrentDirectory(), name + ".json"));

        if (chooser.showSaveDialog(ui) != javax.swing.JFileChooser.APPROVE_OPTION) return;

        rememberFolder(chooser.getSelectedFile());

        try
        {
            java.nio.file.Files.write(chooser.getSelectedFile().toPath(),
                configuration.toString(4).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(ui, String.valueOf(e.getMessage()));
        }
    }

    public void duplicate()
    {
        // NOT WHILE AUTONOMY RUNS, as Import, Export and Delete are not - before anything is asked
        if (ui.refuseWhileARunHoldsTheSetup(ui)) return;

        String from = selected();

        String name = (String) JOptionPane.showInputDialog(ui,
            I18n.t("autosetup.ui.promptConfigurationName"),
            I18n.t("autosetup.ui.menuNewConfiguration"),
            JOptionPane.PLAIN_MESSAGE, null, null, suggestedConfigurationName());

        if (name == null || name.trim().isEmpty()) return;

        // WHERE THE TRAINS STAND AND THE TIMETABLE, ASKED: this is the one door to a new configuration once one exists,
        // and those two are what a variant most often starts without
        int copyTrains = from == null ? JOptionPane.YES_OPTION : askCopyTrains(from);

        if (copyTrains != JOptionPane.YES_OPTION && copyTrains != JOptionPane.NO_OPTION) return;

        // WHERE THE TRAINS ARE, folded into the configuration running first: its file holds where they stood
        // before the last run, and the copy took that
        ui.captureRunningLayout();

        try
        {
            // as a copy, so a variant that differs only in where the locomotives start does not mean
            // re-entering every decision that has nothing to do with that
            session().getStore().createConfiguration(name.trim(), from, copyTrains == JOptionPane.YES_OPTION);
        }
        catch (IOException e)
        {
            // As in initialize: a collision is a collision, and anything else is reported as itself
            JOptionPane.showMessageDialog(ui, nameRefused(e, name, String.valueOf(e.getMessage())));
            return;
        }

        // THE CONFIGURATION RUNNING STAYS THE ONE THE NEXT START RESUMES, as after an import: the window
        // runs it and the exit folds into it.  The copy becomes the one to resume only where none runs.
        String inUse = ui.getActiveDiagramConfiguration();

        session().getStore().setActiveConfiguration(inUse != null ? inUse : name.trim());

        save();
        refresh();
    }

    /**
     * The answer the trains-and-timetable question is given without asking, for a test that is not about the question;
     * null to ask.
     */
    private static volatile Integer copyTrainsAnswerForTests;

    /**
     * @param answer `JOptionPane.YES_OPTION` or `NO_OPTION` to answer New Configuration's question without asking, or
     *        null to ask it again
     */
    public static void answerCopyTrainsForTests(Integer answer)
    {
        copyTrainsAnswerForTests = answer;
    }

    /**
     * Whether New Configuration copies where the trains stand and the timetable.
     *
     * @param from the configuration being copied
     * @return `JOptionPane.YES_OPTION`, `NO_OPTION`, or anything else to make no copy
     */
    private int askCopyTrains(String from)
    {
        Integer given = copyTrainsAnswerForTests;

        if (given != null) return given;

        // The buttons in the language chosen, not the system's: an index, which is Yes, No and Cancel in this order
        Object[] buttons = {I18n.t("ui.yes"), I18n.t("ui.no"), I18n.t("ui.cancel")};

        int chosen = JOptionPane.showOptionDialog(ui, I18n.f("autosetup.ui.promptCopyTrainsAndTimetable", from),
            I18n.t("autosetup.ui.menuNewConfiguration"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
            null, buttons, buttons[0]);

        return chosen == 0 ? JOptionPane.YES_OPTION : chosen == 1 ? JOptionPane.NO_OPTION : JOptionPane.CANCEL_OPTION;
    }

    public void rename()
    {
        // NOT WHILE AUTONOMY RUNS: it renames the running configuration's file under the railway
        if (ui.refuseWhileARunHoldsTheSetup(ui)) return;

        String from = selected();

        if (from == null) return;

        String name = JOptionPane.showInputDialog(ui,
            I18n.t("autosetup.ui.promptConfigurationName"), from);

        if (name == null || name.trim().isEmpty()) return;

        try
        {
            session().getStore().renameConfiguration(from, name.trim());
            save();

            // The window remembers the running configuration BY NAME, and the store has just changed it.
            ui.autonomySetupRenamed(from, name.trim());
        }
        catch (IOException e)
        {
            // The store reports its refusals as message KEYS, so they are translated here rather than
            // shown raw - a user should not be told "autosetup.ui.errorNameInUse".
            JOptionPane.showMessageDialog(ui, nameRefused(e, name, String.valueOf(e.getMessage())));
        }

        refresh();
    }

    public void delete()
    {
        String name = selected();

        // Refused while trains are moving, as deleting the whole setup is: confirming mid-run went through to
        // clearAutoLayout and stopped every locomotive.
        if (ui.refuseWhileARunHoldsTheSetup(ui)) return;

        // WHICH ONE, where there is a choice (OB-319): a setup that cannot be used is refused at its load and never
        // becomes the one in use, so it could not be deleted on its own.  The one in use is offered first.
        java.util.List<String> names = new java.util.ArrayList<>(session().getStore().getConfigurationNames());

        if (names.size() > 1)
        {
            Object chosen = JOptionPane.showInputDialog(ui, I18n.t("autosetup.ui.promptWhichConfigurationToDelete"),
                I18n.t("autosetup.ui.menuDeleteConfiguration"), JOptionPane.QUESTION_MESSAGE, null, names.toArray(),
                names.contains(name) ? name : names.get(0));

            if (chosen == null) return;

            name = chosen.toString();
        }
        else if (names.size() == 1)
        {
            name = names.get(0);
        }

        if (name == null) return;

        // Named in the question, because the list and the running configuration can differ and deleting
        // is not undoable.
        if (JOptionPane.showOptionDialog(ui,
            I18n.f("autosetup.ui.confirmDeleteConfiguration", name),
            I18n.t("autosetup.ui.menuDeleteConfiguration"),
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
            TrainControlUI.YES_NO_OPTS, TrainControlUI.YES_NO_OPTS[1]) != 0) return;

        boolean wasRunning = name.equals(ui.getActiveDiagramConfiguration());

        try
        {
            session().getStore().deleteConfiguration(name);
            save();

            // A layout with none left cannot go on running the graph built from the one just deleted: nothing could
            // save into it.
            if (session().getStore().getConfigurationNames().isEmpty()
                || (wasRunning && ui.getActiveDiagramConfiguration() != null))
            {
                ui.autonomySetupDeleted();
            }
        }
        catch (IOException e)
        {
            // Reported as itself, so a real failure (a permission problem, a sync lock, a full disk) does not look like
            // a rule.
            JOptionPane.showMessageDialog(ui, String.valueOf(e.getMessage()));
        }

        refresh();
    }

    private void save()
    {
        try
        {
            // The reconciliation the save returns is SHOWN, not dropped: it reports what the save had to let go (a
            // setting on a page that is gone, a pairing whose partner has gone), which would otherwise surface later as
            // unexplained behavior.
            AutonomyReport.show(ui, session().save());
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(ui, String.valueOf(e.getMessage()));
        }
    }

    /**
     * Writes the derived graph in the ordinary autonomy format, laid out like the track it came from, to check against
     * the diagram.
     */
    public void inspect()
    {
        try
        {
            java.io.File out = new java.io.File("autonomy-derived.json").getAbsoluteFile();

            java.nio.file.Files.write(out.toPath(),
                session().buildConfigurationForInspection().getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));

            // Say what happened, then show where: a bare path leaves the reader to work out whether it was written or
            // found.
            JOptionPane.showMessageDialog(ui,
                I18n.f("autosetup.ui.infoGraphExported", out.getName()));

            ui.showFileExplorer(out.getParentFile());
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(ui, String.valueOf(e.getMessage()));
        }
    }

    // state

    /**
     * Re-reads everything and shows it.
     */
    public final void refresh()
    {
        // Each step shows only when it is reachable.  Before a setup exists there is nothing to choose
        // and nothing to enable; before a configuration is running there is nowhere to place a train.
        boolean exists = session().exists() || !session().getStore().getConfigurationNames().isEmpty();
        boolean running = ui.getActiveDiagramConfiguration() != null;

        initialize.setVisible(!exists);
        stepChoose.setText(I18n.t(exists
            ? "autosetup.ui.stepChoose" : "autosetup.ui.stepInitialize"));

        configurations.setVisible(exists);
        stepEnable.setVisible(exists);
        enable.setVisible(exists);

        int total = session().getPages().size();

        pagesSummary.setText(I18n.f("autosetup.ui.labelPagesSummary",
            total - session().getStore().getExcludedPages().size(), total));

        // Hidden rather than disabled until it works: an enabled-looking button that goes to a tab
        // which does not exist is what made placing look missing in the first place.
        stepRun.setVisible(running);
        placeLocomotives.setVisible(running);

        if (startHere != null) startHere.setVisible(running);

        status.setText(!exists ? I18n.t("autosetup.ui.statusNeverSetUp")
            : running ? I18n.f("autosetup.ui.statusEnabled", ui.getActiveDiagramConfiguration())
            : I18n.t("autosetup.ui.statusNotEnabled"));

        configurations.removeAllItems();

        for (String name : session().getStore().getConfigurationNames())
        {
            configurations.addItem(name);
        }

        String active = session().getStore().getActiveConfiguration();

        if (active != null) configurations.setSelectedItem(active);

        refreshRoster();
        refreshFindings();
    }

    /**
     * Where the locomotives are, beside the diagram as well as on it: the spatial view answers only for the page being
     * looked at.
     */
    private void refreshRoster()
    {
        roster.clear();

        // ASKED, NOT BUILT: `getAutoLayout` makes a Layout where there is none, and this runs as the panel is made
        // (after Unload, a deleted setup, a railway switch, and every start with nothing loaded), so `hasAutoLayout`
        // answered yes about an empty one.
        Layout layout = ui.getModel() == null ? null : ui.getModel().getAutoLayoutIfLoaded();

        if (layout == null) return;

        for (Point point : layout.getPoints())
        {
            Locomotive locomotive = point.getCurrentLocomotive();

            if (locomotive == null) continue;

            roster.addElement(locomotive.getName() + "  -  " + point.getName());
        }

        if (roster.isEmpty()) roster.addElement(I18n.t("autosetup.ui.infoNoLocomotivesPlaced"));
    }

    /**
     * The list of things to look at: what must be fixed first, then what is only worth checking.  Severity leads and
     * the page groups within it, since the reader arrives asking "why will this not run".
     */
    private void refreshFindings()
    {
        findingsModel.clear();
        findingRows.clear();
        findingRelatedRows.clear();
        findingSeverity.clear();

        // The graph's own problems are among the findings.  Notices are dropped: this panel says whether anything is
        // wrong, and the editor lists the rest.
        Map<AutonomyChecks.Severity, List<Object[]>> sorted = findingRows(session().check(), this::describeTile);

        List<Object[]> errors = sorted.get(AutonomyChecks.Severity.ERROR);
        List<Object[]> warnings = sorted.get(AutonomyChecks.Severity.WARNING);

        // a page renumbered under the setup would silently reattach settings to the wrong track, so it
        // is said out loud rather than left in a getter nobody calls
        for (Map.Entry<String, String> entry : session().getStore().getPageIdConflicts().entrySet())
        {
            warnings.add(new Object[] {null,
                I18n.f("autosetup.ui.warnPageRenumbered", entry.getKey(), entry.getValue())});
        }

        section(I18n.f("autosetup.ui.headingErrors", errors.size()), errors,
            AutonomyChecks.Severity.ERROR);
        section(I18n.f("autosetup.ui.headingWarningsShort", warnings.size()), warnings,
            AutonomyChecks.Severity.WARNING);

        hint.setVisible(!errors.isEmpty() || !warnings.isEmpty());
    }

    /**
     * What a square IS, for a message that would otherwise name a coordinate: a sensor with its s88 address, a switch
     * with its address, plain track.
     */
    private String describeTile(org.traincontrol.automationui.TileGraph.TileKey tile)
    {
        String where = tile.getX() + "," + tile.getY();

        org.traincontrol.base.LayoutDiagramComponent component =
            session().getGraph() == null ? null : session().getGraph().getTiles().get(tile);

        if (component == null) return where;

        // a name the user gave it beats anything derived
        String named = session().getStore().getPointName(tile);

        if (named != null && !named.trim().isEmpty()) return named.trim() + " (" + where + ")";

        if (component.isFeedback())
        {
            return I18n.f("autosetup.ui.describeSensor", where, component.getRawAddress());
        }

        if (component.getAccessory() != null)
        {
            return I18n.f("autosetup.ui.describeAccessory",
                component.getUserFriendlyTypeName(), where, component.getAddress());
        }

        return I18n.f("autosetup.ui.describeTile", component.getUserFriendlyTypeName(), where);
    }

    /**
     * Adds one severity section, with its findings grouped under the page each is on.
     */
    private void section(String heading, List<Object[]> rows, AutonomyChecks.Severity severity)
    {
        if (rows.isEmpty()) return;

        findingsModel.addElement(heading);
        findingRows.add(null);
        findingRelatedRows.add(null);
        findingSeverity.add(null);

        Map<String, List<Object[]>> byPage = new java.util.LinkedHashMap<>();

        for (Object[] row : rows)
        {
            org.traincontrol.automationui.TileGraph.TileKey tile =
                (org.traincontrol.automationui.TileGraph.TileKey) row[0];

            String page = tile == null ? "" : tile.getPage();

            if (!byPage.containsKey(page)) byPage.put(page, new java.util.ArrayList<Object[]>());

            byPage.get(page).add(row);
        }

        for (Map.Entry<String, List<Object[]>> entry : byPage.entrySet())
        {
            // no page heading for findings that belong to the whole layout rather than a square
            if (!entry.getKey().isEmpty())
            {
                findingsModel.addElement("  " + I18n.f("autosetup.ui.labelPageHeading",
                    entry.getKey()));
                findingRows.add(null);
                findingRelatedRows.add(null);
                findingSeverity.add(null);
            }

            for (Object[] row : entry.getValue())
            {
                findingsModel.addElement("     " + row[1]);
                findingRows.add((org.traincontrol.automationui.TileGraph.TileKey) row[0]);
                findingSeverity.add(severity);

                @SuppressWarnings("unchecked")
                List<org.traincontrol.automationui.TileGraph.TileKey> related = row.length > 2
                    ? (List<org.traincontrol.automationui.TileGraph.TileKey>) row[2] : null;

                findingRelatedRows.add(related);
            }
        }
    }

    private static String describe(String key, Object... args)
    {
        try
        {
            return I18n.f(key, args);
        }
        catch (RuntimeException e)
        {
            // a key that has not reached the bundles yet should not blank the whole list
            return key + " " + (args.length > 0 ? args[0] : "");
        }
    }

    /**
     * The findings as rows {tile, text, related} by severity, in their order: the walk both finding lists make.
     *
     * A finding about a square says what the square is in {0}, as an unnamed Point's name is its coordinate, and keeps
     * its own subject in {1}, as {0} alone cannot say which train (OB-153).
     *
     * @param findings what the check found
     * @param describeTile what a square is
     * @return the ERROR, WARNING and NOTICE rows, every other severity among the notices
     */
    static Map<AutonomyChecks.Severity, List<Object[]>> findingRows(List<AutonomyChecks.Finding> findings,
        java.util.function.Function<org.traincontrol.automationui.TileGraph.TileKey, String> describeTile)
    {
        Map<AutonomyChecks.Severity, List<Object[]>> sorted = new java.util.EnumMap<>(AutonomyChecks.Severity.class);

        sorted.put(AutonomyChecks.Severity.ERROR, new java.util.ArrayList<>());
        sorted.put(AutonomyChecks.Severity.WARNING, new java.util.ArrayList<>());
        sorted.put(AutonomyChecks.Severity.NOTICE, new java.util.ArrayList<>());

        for (AutonomyChecks.Finding finding : findings)
        {
            String subject = finding.getTile() == null
                ? finding.getSubject() : describeTile.apply(finding.getAbout());

            String text = describe(finding.getMessageKey(), subject, finding.getSubject(),
                finding.getCount(), finding.getDetail(), finding.getThird());

            AutonomyChecks.Severity severity = finding.getSeverity();

            sorted.get(severity == AutonomyChecks.Severity.ERROR || severity == AutonomyChecks.Severity.WARNING
                ? severity : AutonomyChecks.Severity.NOTICE)
                .add(new Object[] {finding.getTile(), text, finding.getRelated()});
        }

        return sorted;
    }

}
