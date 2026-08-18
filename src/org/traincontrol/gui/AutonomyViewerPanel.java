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
import org.traincontrol.util.I18n;

/**
 * Managing a layout's autonomy configurations, from the Auto tab.
 *
 * The counterpart to the editor panel: that one is for deciding how the railway is wired, this one is
 * for using it.  Setting autonomy up for the first time, choosing which configuration runs, moving
 * configurations between machines, and checking whether anything looks wrong before trusting trains
 * to it.  It stands where the JSON window used to: the configuration IS the diagram now, so what was a
 * text area becomes a list of named setups.
 *
 * Hand written and mounted into a container the main window already has, so no form changes and no new
 * window.
 *
 * @author Adam
 */
public class AutonomyViewerPanel extends JPanel
{
    private final AutonomySession session;

    /**
     * The main window - and the parent of every dialog this class raises.
     *
     * It has to be, because this panel is no longer displayed: its controls moved to the Autonomy
     * menu and the object stayed behind as the place the actions live.  A dialog parented to a
     * component that is not on screen has nothing to centre on, so every one of them opened in the
     * middle of the DISPLAY instead of over the window that asked.
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

    // What each findings row is, so the renderer can colour it and a click can act on it.  Parallel
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

    // Set while a configuration is being loaded into the combo, so reacting to that does not load it
    // straight back again
    private boolean populating = false;

    public AutonomyViewerPanel(AutonomySession session, TrainControlUI ui)
    {
        this.session = session;
        this.ui = ui;

        // White, and no inset border: this panel stands inside a white container, and a default grey
        // JPanel with padding read as a second surface floating on top of it.
        setOpaque(true);
        setBackground(java.awt.Color.WHITE);
        setLayout(new BorderLayout(0, 6));

        add(buildSteps(), BorderLayout.NORTH);
        add(buildFindings(), BorderLayout.CENTER);

        // No preferred size at all: the scroll pane it lives in has scrolling switched off in both
        // directions, so the viewport hands this panel exactly the space the tab has and the findings
        // list - the one thing that can overflow - scrolls inside its own box instead.
        setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        unfocusable(this);

        refresh();
    }

    // Copied from the Autonomy Settings tab rather than invented here, so the two read as one design:
    //   group heading  jLabel51 "Train Behavior"        Segoe UI Semibold 13, navy, above a boxed panel
    //   field label    jLabel46 "Minimum Action Delay"  Segoe UI plain 14, navy
    //   control        minDelay, atomicRoutes           Segoe UI plain 14, default colour
    //   button         validateButton, editLayoutButton Segoe UI bold 12
    //   box            jPanel3, jPanel4                 white, 1px line border in (204,204,204)
    static final java.awt.Font FONT = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14);

    static final java.awt.Font FONT_GROUP = new java.awt.Font("Segoe UI Semibold", java.awt.Font.PLAIN, 13);

    static final java.awt.Font FONT_BOLD = new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12);

    /**
     * The findings and roster lists only.  They are dense reading rather than labels, and at 14 a real
     * layout's list stops fitting in the space there is.
     */
    static final java.awt.Font FONT_LIST = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12);

    static final java.awt.Color HEADING_COLOUR = new java.awt.Color(0, 0, 115);
    static final java.awt.Color BOX_BORDER = new java.awt.Color(204, 204, 204);

    static final java.awt.Color ERROR_COLOUR = new java.awt.Color(170, 0, 0);
    static final java.awt.Color WARNING_COLOUR = new java.awt.Color(150, 95, 0);
    static final java.awt.Color SUBHEADING_COLOUR = new java.awt.Color(70, 70, 70);

    /**
     * Takes a whole panel out of the keyboard's reach, and returns it.
     *
     * The main window drives locomotives from bare key presses, so a button or a checkbox that can
     * take focus is a trap: press it once and every subsequent keystroke goes to that control instead
     * of to the train.  Nothing on either autonomy panel is operated by keyboard - they are read and
     * clicked - so the whole tree gives focus up.  This matches what the rest of the window already
     * does control by control; done here as a sweep so a control added later cannot forget.
     *
     * Menus are untouched on purpose: a popup takes focus while it is open and gives it back when it
     * closes, which is how a menu is meant to behave.
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

    /**
     * Applies the application's control font to a component, and returns it.
     *
     * @param component
     * @param bold true for a button, false for a label or control
     */
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

    /**
     * All the step buttons share a width, so the left edge of the box reads as one column rather than
     * as three buttons that happen to start in the same place and end wherever their text did.
     */
    private JButton sized(JButton button)
    {
        styled(button, true);
        button.setPreferredSize(new Dimension(BUTTON_WIDTH, 26));
        return button;
    }

    static final int BUTTON_WIDTH = 170;

    /**
     * The workflow: choose, scope, enable, run - each step a field label with its controls beneath it,
     * laid out on one grid so every label and every control starts on the same left edge.
     *
     * Built as a boxed group under a heading, which is how the Autonomy Settings tab next door presents
     * exactly this kind of content.
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

        // Everything that manages configurations lives behind one button and the combo's own context
        // menu, rather than as five peers competing with the step that matters.  They are all rare:
        // a configuration is made once and chosen thereafter.
        final JButton manage = new JButton(I18n.t("autosetup.ui.btnManage"));
        manage.addActionListener(e -> manageMenu().show(manage, 0, manage.getHeight()));
        choose.add(styled(manage, true));

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
        // On the surface rather than on the Manage menu: it decides which track is considered at all,
        // so it is the first thing to reach for when the list below is full of findings about a page
        // that is not part of the railway being automated.
        add(panel, gbc, stepPages, 10);

        JPanel pageRow = row();

        JButton pages = new JButton(I18n.t("autosetup.ui.btnExcludePage"));
        pages.addActionListener(e -> choosePages());
        pageRow.add(sized(pages));
        pageRow.add(styled(pagesSummary, false));

        add(panel, gbc, pageRow, 0);

        // --- step 2: turn it on ---
        add(panel, gbc, stepEnable, 10);

        enable.setToolTipText(I18n.t("autosetup.ui.tooltipLoadConfiguration"));
        enable.addActionListener(e ->
        {
            Object selected = configurations.getSelectedItem();

            if (selected != null) load(String.valueOf(selected), true);
        });

        JPanel enableRow = row();
        enableRow.add(sized(enable));
        enableRow.add(styled(status, false));

        add(panel, gbc, enableRow, 0);

        // --- step 3: run ---
        add(panel, gbc, stepRun, 10);

        JPanel run = row();

        // Sends the user where placing actually happens: the diagram editor, where a station can be
        // right-clicked and given a locomotive.  It used to open the run tab, which is where autonomy
        // is STARTED - a train has to be somewhere before that is any use.
        placeLocomotives.addActionListener(e -> ui.openAutonomyEditor(null));
        run.add(sized(placeLocomotives));

        // The step after placing, kept as its own button.  Placing used to lead here directly, and
        // moving it to the editor would otherwise have left the run tab with nothing pointing at it -
        // the same dead end this workflow was built to close.
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
        menu.addSeparator();
        menu.add(item(I18n.t("autosetup.ui.btnExcludePage"), new Runnable()
            { public void run() { choosePages(); } }));

        // Debug builds only, and last on the menu.  What it writes is the DERIVED graph in the old
        // JSON form - a diagnostic for reading when something derives wrongly, not a file anybody
        // operates the railway from.  The menu is rebuilt on every press, so the flag is read fresh.
        if (ui.getModel() != null && ui.getModel().isDebug())
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
     * The list of things to look at, errors first, each one clickable.
     *
     * A boxed group like the one above it, and the only part of this tab that is allowed to overflow:
     * the list scrolls inside its own box, so the tab itself never does.
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

        findings.setCellRenderer(new FindingRenderer());
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

                // locationToIndex returns the CLOSEST row, not -1, for a click below the last one - so
                // clicking the empty space under the list opened the last finding's tile.
                java.awt.Rectangle bounds = findings.getCellBounds(row, row);

                if (bounds == null || !bounds.contains(e.getPoint())) return;

                org.traincontrol.automationui.TileGraph.TileKey tile = findingRows.get(row);

                if (tile != null) ui.openAutonomyEditor(tile);
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
     * Colours a row by what it is: a section heading, something that must be fixed, or something worth
     * checking.  Colour rather than a prefix, because the list is scanned rather than read.
     */
    private class FindingRenderer extends javax.swing.DefaultListCellRenderer
    {
        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean isSelected, boolean cellHasFocus)
        {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            AutonomyChecks.Severity severity =
                index < findingSeverity.size() ? findingSeverity.get(index) : null;

            boolean heading = index < findingRows.size()
                && findingRows.get(index) == null && severity == null;

            setFont(heading ? FONT_BOLD : FONT);

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

    /**
     * Chooses which pages autonomy uses.
     *
     * Here rather than in the editor because it is a property of the whole setup, and because the
     * commonest reason to reach for it - a page full of findings that is not part of the railway being
     * automated - is discovered while reading this list.
     */
    public void choosePages()
    {
        java.util.List<org.traincontrol.base.LayoutDiagram> pages = session.getPages();

        // BoxLayout, not GridLayout: a grid gives every row the height of its tallest, so the wrapped
        // prompt made each page checkbox as tall as three lines of text and the dialog filled the
        // screen.  The prompt is wrapped to a fixed width for the same reason - as one long line it
        // set the dialog's width all by itself.
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        JLabel prompt = styled(new JLabel("<html><body style='width:320px'>"
                + I18n.t("autosetup.ui.promptExcludePage") + "</body></html>"), false);
        prompt.setAlignmentX(LEFT_ALIGNMENT);
        prompt.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 6, 0));
        panel.add(prompt);

        java.util.Map<String, javax.swing.JCheckBox> boxes = new java.util.LinkedHashMap<>();

        for (org.traincontrol.base.LayoutDiagram page : pages)
        {
            boolean excluded = session.getStore().getExcludedPages().contains(page.getName());

            javax.swing.JCheckBox box = new javax.swing.JCheckBox(page.getName(), !excluded);
            box.setAlignmentX(LEFT_ALIGNMENT);
            styled(box, false);
            boxes.put(page.getName(), box);
            panel.add(box);
        }

        if (JOptionPane.showConfirmDialog(ui, panel,
            I18n.t("autosetup.ui.btnExcludePage"),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
        {
            return;
        }

        for (java.util.Map.Entry<String, javax.swing.JCheckBox> entry : boxes.entrySet())
        {
            session.setPageExcluded(entry.getKey(), !entry.getValue().isSelected());
        }

        save();
        refresh();
    }

    // --- configurations ---------------------------------------------------------------------------

    /**
     * Loads a configuration and makes it the one that runs.
     *
     * Refused while autonomy is doing anything, because replacing the graph underneath a train in
     * motion leaves it running with nothing tracking it - the same gate the rest of the application
     * applies to structural changes.
     */
    /**
     * Which configuration the Manage actions operate on: the one showing in the dropdown.
     *
     * NOT the active one.  Every Manage action used to read getActiveConfiguration(), so choosing "Yard"
     * in the list and pressing Delete deleted whatever was running - unrecoverable, from an ordinary
     * gesture.  The dropdown is what the user is pointing at.
     */
    /**
     * Points the Manage actions at a configuration, for a caller that has no combo box of its own.
     *
     * @param name
     */
    public void setSelectedConfiguration(String name)
    {
        populating = true;

        try
        {
            configurations.setSelectedItem(name);
        }
        finally
        {
            populating = false;
        }
    }

    public String selected()
    {
        Object chosen = configurations.getSelectedItem();

        return chosen == null ? session.getStore().getActiveConfiguration() : String.valueOf(chosen);
    }

    /**
     * Loads the active configuration - the startup resume, which is the same as the user choosing what
     * they chose last time.
     */
    public void loadActive()
    {
        String active = session.getStore().getActiveConfiguration();

        // Quietly: a modal on startup, before the window is even up, tells a user who has not asked for
        // anything that something they have never heard of cannot be used.  A failed resume goes to the
        // log and leaves its reasons in the list, where somebody looking for them will find them.
        if (active != null) load(active, false);
    }

    /**
     * @param name the configuration to load
     * @param interactive whether the user asked for this, and so should be told when it fails
     */
    public void load(String name, boolean interactive)
    {
        // The same gate the JSON path applies before replacing the layout: confirm, then stop whatever
        // is moving.  Owned by the main window because stopping trains is its business, not a panel's.
        if (!ui.prepareAutonomyReload())
        {
            refresh();
            return;
        }

        // What was set while the outgoing configuration ran - placements, homes, settings - goes back
        // into THAT configuration, by name, before anything is replaced.  By name because store-active
        // and what-is-running can disagree after a refused load, and capturing into the store's idea
        // of active would overwrite a configuration with another one's state.
        if (ui.getActiveDiagramConfiguration() != null && ui.getModel() != null
            && ui.getModel().hasAutoLayout() && ui.getModel().getAutoLayout().isValid())
        {
            try
            {
                session.captureFromLayout(ui.getModel().getAutoLayout().toJSON(),
                    ui.getActiveDiagramConfiguration());
            }
            catch (Exception e)
            {
                // capture is a courtesy; failing to capture must not block loading
                if (ui.getModel().isDebug()) ui.getModel().log(String.valueOf(e.getMessage()));
            }
        }

        // remembered so a failed load can put the store back: what loads on the next start must be
        // something that actually loaded, not something that was refused partway
        String previous = session.getStore().getActiveConfiguration();

        session.getStore().setActiveConfiguration(name);
        session.rebuild();

        if (session.hasBlockingProblems())
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
            else if (ui.getModel() != null)
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
            ui.getModel().parseAuto(session.buildConfiguration());

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

    private int countBlocking()
    {
        int blocking = 0;

        for (org.traincontrol.automationui.TileGraph.Problem problem : session.getGraph().getProblems())
        {
            if (problem.isBlocking()) blocking++;
        }

        return blocking;
    }

    private void revert(String previous)
    {
        session.getStore().setActiveConfiguration(previous);
        session.rebuild();
    }

    /**
     * Sets autonomy up for a layout that has none: one configuration, named by the user, which
     * everything else on this panel then applies to.
     */
    public void initialize()
    {
        String name = (String) JOptionPane.showInputDialog(ui,
            I18n.t("autosetup.ui.promptConfigurationName"),
            I18n.t("autosetup.ui.menuInitialize"),
            JOptionPane.PLAIN_MESSAGE, null, null, suggestedConfigurationName());

        if (name == null || name.trim().isEmpty()) return;

        try
        {
            session.initialize(name.trim());
        }
        catch (IOException e)
        {
            // Only a name collision is reported as one.  initialize() also SAVES, so a full disk or a
            // read-only folder used to be announced as "that name is already in use" - and the user
            // would try another name, and another, none of which was ever the problem.
            JOptionPane.showMessageDialog(ui,
                AutonomyCompanionStore.ERROR_NAME_IN_USE.equals(e.getMessage())
                    ? I18n.f("autosetup.ui.errorNameInUse", name.trim())
                    : String.valueOf(e.getMessage()));
            return;
        }

        refresh();

        // And run it.  Creating a configuration nobody is using leaves the window showing the old one
        // with nothing to say a new one exists - which reads exactly like the button having done
        // nothing.  load() is the same guarded path the chooser uses, so trains are stopped and the
        // user is asked before anything is replaced.
        load(name.trim(), true);

        // A refused load must still leave the new configuration CHOSEN.
        //
        // load() puts the store back to whatever was active before, so that what resumes next start is
        // something that actually loaded - right in general, and wrong for the one just created: what
        // was active before is nothing, so the setup the user has this second made is left unchosen.
        // The editor is gated on a configuration being chosen, so the errors it exists to fix would lock
        // the door to the room they are fixed in, and the banner would go on offering to load something
        // that cannot load.
        if (ui.getActiveDiagramConfiguration() == null)
        {
            session.getStore().setActiveConfiguration(name.trim());
            session.rebuild();

            refresh();
        }

        // Then show them the thing they just made autonomy for.
        //
        // Whatever happened above - loaded, or refused with a list of what to fix - the answer is on the
        // track diagram: the stations appear there, and the strip along the top says how many things
        // need looking at and opens the editor at the first of them.  Staying on this tab leaves the
        // work they just did apparently invisible.
        ui.showLayoutTab();
    }

    /**
     * A name the user can simply accept.
     *
     * Naming a thing is a small decision, and it is the first one this feature asks for - so a blank box
     * makes somebody invent something before they can find out what the feature does.  "Autonomy 1", or
     * the next number nobody has used, can be accepted in one keystroke and renamed later from the same
     * menu.
     *
     * The next FREE number, not the count plus one: with "Autonomy 1" and "Autonomy 3" present, the
     * count would suggest "Autonomy 3" and the dialog would then refuse it as already in use.
     */
    private String suggestedConfigurationName()
    {
        java.util.List<String> taken = session.getStore().getConfigurationNames();

        for (int next = 1; next < 1000; next++)
        {
            String candidate = I18n.f("autosetup.ui.defaultConfigurationName", next);

            if (!taken.contains(candidate)) return candidate;
        }

        return I18n.f("autosetup.ui.defaultConfigurationName", taken.size() + 1);
    }

    /**
     * Reads an exported configuration file in as a new named configuration.
     *
     * The file carries the configuration AND the track decisions it refers to - station names, which
     * squares are stations, lengths, directions, portals, captions.  It used to carry the configuration
     * alone, on the assumption that the track is derived from the diagram; the track's SHAPE is, but
     * what any of it is called and which squares count as stations are decisions somebody made, and
     * they live only in the setup.  Without them an imported configuration refers to nothing.
     *
     * The shared half is merged, never adopted wholesale, so importing cannot rename a station that
     * already has a name here.
     */
    public void importConfiguration()
    {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();

        if (chooser.showOpenDialog(ui) != javax.swing.JFileChooser.APPROVE_OPTION) return;

        String name = JOptionPane.showInputDialog(ui, I18n.t("autosetup.ui.promptImportName"),
            chooser.getSelectedFile().getName().replaceAll("\\.json$", ""));

        if (name == null || name.trim().isEmpty()) return;

        // importing over an existing name replaces it, which is sometimes wanted and never silent
        if (session.getStore().getConfigurationNames().contains(name.trim()))
        {
            int replace = JOptionPane.showConfirmDialog(ui,
                I18n.f("autosetup.ui.confirmImportOverwrites", name.trim()),
                I18n.t("autosetup.ui.title"), JOptionPane.YES_NO_OPTION);

            if (replace != JOptionPane.YES_OPTION) return;
        }

        try
        {
            byte[] bytes = java.nio.file.Files.readAllBytes(chooser.getSelectedFile().toPath());

            int filled = session.getStore().importBundle(name.trim(),
                new org.json.JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));

            save();

            // Said out loud, because the alternative is somebody wondering whether their station names
            // came across.  A file written before exporting carried the track decisions fills nothing,
            // and that is worth knowing too.
            JOptionPane.showMessageDialog(ui, I18n.f("autosetup.ui.infoImported", filled));
        }
        catch (IOException | RuntimeException e)
        {
            JOptionPane.showMessageDialog(ui,
                I18n.f("autosetup.ui.errorImportUnreadable", String.valueOf(e.getMessage())));
        }

        refresh();
    }

    /**
     * Reads an old autonomy.json onto the squares carrying the same sensors - names, stations,
     * lengths, and the locomotives that were standing on them.
     *
     * The way across for anybody who set their railway up in the graph this replaces: none of it is
     * derivable from a diagram, so without this it would all be entered again, one square at a time.
     *
     * Debug builds only - the sensor match is only as good as the diagram's addresses, so this is for
     * somebody who will check the result rather than a button on everybody's menu.
     */
    public void importLegacy()
    {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();

        if (chooser.showOpenDialog(ui) != javax.swing.JFileChooser.APPROVE_OPTION) return;

        try
        {
            byte[] bytes = java.nio.file.Files.readAllBytes(chooser.getSelectedFile().toPath());

            AutonomySession.LegacyImport result = session.importLegacy(
                new org.json.JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));

            save();

            String unmatched = result.unmatched.isEmpty()
                ? "" : "\n\n" + String.join(", ", result.unmatched);

            JOptionPane.showMessageDialog(ui, I18n.f("autosetup.ui.infoLegacyImported",
                result.matched, result.placed, result.skipped, result.unmatched.size()) + unmatched);
        }
        catch (IOException | RuntimeException e)
        {
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
        String name = selected();

        if (name == null) return;

        org.json.JSONObject configuration = session.getStore().exportBundle(name);

        if (configuration == null) return;

        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setSelectedFile(new java.io.File(name + ".json"));

        if (chooser.showSaveDialog(ui) != javax.swing.JFileChooser.APPROVE_OPTION) return;

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
        String from = selected();

        String name = (String) JOptionPane.showInputDialog(ui,
            I18n.t("autosetup.ui.promptConfigurationName"),
            I18n.t("autosetup.ui.menuNewConfiguration"),
            JOptionPane.PLAIN_MESSAGE, null, null, suggestedConfigurationName());

        if (name == null || name.trim().isEmpty()) return;

        try
        {
            // as a copy, so a variant that differs only in where the locomotives start does not mean
            // re-entering every decision that has nothing to do with that
            session.getStore().createConfiguration(name.trim(), from);
        }
        catch (IOException e)
        {
            // As in initialize: a collision is a collision, and anything else is reported as itself
            JOptionPane.showMessageDialog(ui,
                AutonomyCompanionStore.ERROR_NAME_IN_USE.equals(e.getMessage())
                    ? I18n.f("autosetup.ui.errorNameInUse", name.trim())
                    : String.valueOf(e.getMessage()));
            return;
        }

        session.getStore().setActiveConfiguration(name.trim());

        save();
        refresh();
    }

    public void rename()
    {
        String from = selected();

        if (from == null) return;

        String name = JOptionPane.showInputDialog(ui,
            I18n.t("autosetup.ui.promptConfigurationName"), from);

        if (name == null || name.trim().isEmpty()) return;

        try
        {
            session.getStore().renameConfiguration(from, name.trim());
            save();
        }
        catch (IOException e)
        {
            // The store reports its refusals as message KEYS, so they are translated here rather than
            // shown raw - a user should not be told "autosetup.ui.errorNameInUse".
            JOptionPane.showMessageDialog(ui,
                AutonomyCompanionStore.ERROR_NAME_IN_USE.equals(e.getMessage())
                    ? I18n.f("autosetup.ui.errorNameInUse", name.trim())
                    : String.valueOf(e.getMessage()));
        }

        refresh();
    }

    public void delete()
    {
        String name = selected();

        if (name == null) return;

        // Named in the question, because the list and the running configuration can differ and deleting
        // is not undoable.
        if (JOptionPane.showConfirmDialog(ui,
            I18n.f("autosetup.ui.confirmDeleteConfiguration", name),
            I18n.t("autosetup.ui.menuDeleteConfiguration"),
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

        boolean wasRunning = name.equals(ui.getActiveDiagramConfiguration());

        try
        {
            session.getStore().deleteConfiguration(name);
            save();

            // Deleting the last one is allowed now, and a layout with none left cannot go on running
            // the graph built from the one just deleted - nothing would be able to save into it, and
            // the diagram would keep showing stations belonging to a configuration that is gone.
            if (session.getStore().getConfigurationNames().isEmpty()
                || (wasRunning && ui.getActiveDiagramConfiguration() != null))
            {
                ui.autonomySetupDeleted();
            }
        }
        catch (IOException e)
        {
            // The last configuration cannot go: a setup with none is a state nothing here could act on.
            // Any OTHER failure - a permission problem, a full disk - is reported as itself rather than
            // blamed on that rule.
            JOptionPane.showMessageDialog(ui,
                AutonomyCompanionStore.ERROR_LAST_CONFIGURATION.equals(e.getMessage())
                    ? I18n.t("autosetup.ui.errorLastConfiguration")
                    : String.valueOf(e.getMessage()));
        }

        refresh();
    }

    private void save()
    {
        try
        {
            session.save();
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(ui, String.valueOf(e.getMessage()));
        }
    }

    /**
     * Writes the derived graph out in the ordinary autonomy format, laid out like the track it came
     * from, so it can be read and checked against the diagram beside it.
     */
    public void inspect()
    {
        try
        {
            java.io.File out = new java.io.File("autonomy-derived.json").getAbsoluteFile();

            java.nio.file.Files.write(out.toPath(),
                session.buildConfigurationForInspection().getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));

            // Say what happened, then show where.  A dialog containing nothing but a path leaves the
            // reader to work out whether it was written, found, or about to be overwritten - and then
            // to go and find the folder themselves.  Same two steps the update download takes.
            JOptionPane.showMessageDialog(ui,
                I18n.f("autosetup.ui.infoGraphExported", out.getName(), out.getParent()));

            ui.showFileExplorer(out.getParentFile());
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(ui, String.valueOf(e.getMessage()));
        }
    }

    // --- state ------------------------------------------------------------------------------------

    /**
     * Re-reads everything and shows it.
     */
    public final void refresh()
    {
        // Each step shows only when it is reachable.  Before a setup exists there is nothing to choose
        // and nothing to enable; before a configuration is running there is nowhere to place a train.
        boolean exists = session.exists() || !session.getStore().getConfigurationNames().isEmpty();
        boolean running = ui.getActiveDiagramConfiguration() != null;

        initialize.setVisible(!exists);
        stepChoose.setText(I18n.t(exists
            ? "autosetup.ui.stepChoose" : "autosetup.ui.stepInitialize"));

        configurations.setVisible(exists);
        stepEnable.setVisible(exists);
        enable.setVisible(exists);

        int total = session.getPages().size();

        pagesSummary.setText(I18n.f("autosetup.ui.labelPagesSummary",
            total - session.getStore().getExcludedPages().size(), total));

        // Hidden rather than disabled until it works: an enabled-looking button that goes to a tab
        // which does not exist is what made placing look missing in the first place.
        stepRun.setVisible(running);
        placeLocomotives.setVisible(running);

        if (startHere != null) startHere.setVisible(running);

        status.setText(!exists ? I18n.t("autosetup.ui.statusNeverSetUp")
            : running ? I18n.f("autosetup.ui.statusEnabled", ui.getActiveDiagramConfiguration())
            : I18n.t("autosetup.ui.statusNotEnabled"));

        populating = true;

        try
        {
            configurations.removeAllItems();

            for (String name : session.getStore().getConfigurationNames())
            {
                configurations.addItem(name);
            }

            String active = session.getStore().getActiveConfiguration();

            if (active != null) configurations.setSelectedItem(active);
        }
        finally
        {
            populating = false;
        }

        refreshRoster();
        refreshFindings();
    }

    /**
     * Where the locomotives are.
     *
     * Beside the diagram as well as on it, because the spatial view can only answer the question for
     * the page being looked at - and a locomotive parked on another page is exactly the one somebody is
     * hunting for.
     */
    private void refreshRoster()
    {
        roster.clear();

        Layout layout = ui.getModel() == null ? null : ui.getModel().getAutoLayout();

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
     * The list of things to look at: what must be fixed first, then what is only worth checking.
     *
     * Severity leads and the page groups within it, rather than the other way round.  The question a
     * reader arrives with is "why will this not run", and a list led by page makes them read every
     * line of every page to find the two that answer it.  Anything below the first section is by
     * definition optional, which is what makes the list skimmable rather than a wall.
     */
    private void refreshFindings()
    {
        findingsModel.clear();
        findingRows.clear();
        findingSeverity.clear();

        List<Object[]> errors = new java.util.ArrayList<>();
        List<Object[]> warnings = new java.util.ArrayList<>();

        // A graph problem is what stops the build, so it is always an error.
        for (org.traincontrol.automationui.TileGraph.Problem problem : session.getGraph() == null
            ? java.util.Collections.<org.traincontrol.automationui.TileGraph.Problem>emptyList()
            : session.getGraph().getProblems())
        {
            (problem.isBlocking() ? errors : warnings).add(new Object[]
            {
                problem.getTile(),
                describe(problem.getMessageKey(), problem.getTile() == null
                    ? "" : describeTile(problem.getTile()))
            });
        }

        for (AutonomyChecks.Finding finding : session.check())
        {
            // The subject is usually a Point name, and an unnamed Point's name is its coordinate - so
            // where there is a tile, say what the tile is instead.
            String subject = finding.getTile() == null
                ? finding.getSubject() : describeTile(finding.getTile());

            (finding.getSeverity() == AutonomyChecks.Severity.ERROR ? errors : warnings).add(
                new Object[] {finding.getTile(), describe(finding.getMessageKey(), subject)});
        }

        // a page renumbered under the setup would silently reattach settings to the wrong track, so it
        // is said out loud rather than left in a getter nobody calls
        for (Map.Entry<String, String> entry : session.getStore().getPageIdConflicts().entrySet())
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
     * What a square IS, for a message that would otherwise name a coordinate.
     *
     * A finding that says "nothing connects to 1 - Main 8,6" tells the reader where to look and nothing
     * about what they will find when they get there.  Naming the tile - a sensor with an s88 address, a
     * switch with its address, a piece of plain track - is usually enough to recognise the spot without
     * going to it, and is what makes a list of eleven skimmable.
     */
    private String describeTile(org.traincontrol.automationui.TileGraph.TileKey tile)
    {
        String where = tile.getX() + "," + tile.getY();

        org.traincontrol.base.LayoutDiagramComponent component =
            session.getGraph() == null ? null : session.getGraph().getTiles().get(tile);

        if (component == null) return where;

        // a name the user gave it beats anything derived
        String named = session.getStore().getPointName(tile);

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
                findingSeverity.add(null);
            }

            for (Object[] row : entry.getValue())
            {
                findingsModel.addElement("     " + row[1]);
                findingRows.add((org.traincontrol.automationui.TileGraph.TileKey) row[0]);
                findingSeverity.add(severity);
            }
        }
    }

    private String describe(String key, String subject)
    {
        try
        {
            return I18n.f(key, subject);
        }
        catch (RuntimeException e)
        {
            return key + " " + subject;
        }
    }

}
