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
import org.traincontrol.base.AutonomyChecks;
import org.traincontrol.base.AutonomySession;
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
    private final TrainControlUI ui;

    private final JComboBox<String> configurations = new JComboBox<>();
    private final DefaultListModel<String> roster = new DefaultListModel<>();
    private final DefaultListModel<String> findingsModel = new DefaultListModel<>();
    private final JList<String> findings = new JList<>(findingsModel);

    // Which tile each row of the findings list refers to; null for a page heading.  Parallel to the
    // model rather than a richer element type, so the list still renders as plain strings.
    private final List<org.traincontrol.base.TileGraph.TileKey> findingRows =
        new java.util.ArrayList<>();

    // What each findings row is, so the renderer can colour it and a click can act on it.  Parallel
    // lists rather than a richer element type, so the list still renders as plain strings.
    private final List<AutonomyChecks.Severity> findingSeverity = new java.util.ArrayList<>();

    private final JButton initialize = new JButton(I18n.t("autosetup.ui.btnInitFromLayout"));
    private final JButton enable = new JButton(I18n.t("autosetup.ui.btnEnable"));
    private final JButton placeLocomotives =
        new JButton(I18n.t("autosetup.ui.btnPlaceLocomotives"));

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

        // Pinned, so the panel never asks its scroll pane for more width than the tab has.  Without
        // this a single long finding widened the whole panel and the entire tab scrolled sideways.
        setPreferredSize(new Dimension(PANEL_WIDTH, 620));
        setMaximumSize(new Dimension(PANEL_WIDTH, Integer.MAX_VALUE));

        refresh();
    }

    /**
     * How wide the panel asks to be.  Everything inside is bounded by this; only the findings list is
     * allowed to overflow it, and it does so inside its own scroll pane.
     */
    static final int PANEL_WIDTH = 420;

    // Taken from the rest of the window rather than chosen here: section headings are plain 14 in navy
    // (layoutListLabel, sizeLabel, "Locomotive Key Mapping"), buttons are bold 12 (editLayoutButton,
    // validateButton), ordinary labels are plain 14.
    static final java.awt.Font FONT = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14);

    static final java.awt.Font FONT_BOLD = new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12);

    /**
     * The findings list only.  It is dense tabular reading rather than a label, and at 14 a real
     * layout's list stops fitting on a screen.
     */
    static final java.awt.Font FONT_LIST = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12);

    static final java.awt.Color HEADING_COLOUR = new java.awt.Color(0, 0, 115);

    static final java.awt.Color ERROR_COLOUR = new java.awt.Color(170, 0, 0);
    static final java.awt.Color WARNING_COLOUR = new java.awt.Color(150, 95, 0);
    static final java.awt.Color SUBHEADING_COLOUR = new java.awt.Color(70, 70, 70);

    /**
     * Applies the application's control font to a component, and returns it.
     *
     * @param component
     * @param bold true for a button, false for a label
     */
    static <T extends javax.swing.JComponent> T styled(T component, boolean bold)
    {
        component.setFont(bold ? FONT_BOLD : FONT);
        return component;
    }

    /**
     * A section heading in the window's own style: plain 14, navy, as used everywhere else.
     */
    private JLabel step(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FONT);
        label.setForeground(HEADING_COLOUR);
        label.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        return label;
    }

    private JPanel row()
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.setOpaque(false);
        return panel;
    }

    /**
     * The three steps, in order, each doing one thing.
     *
     * Laid out as a sequence rather than as a panel of controls because the order is not obvious from
     * the controls themselves - the first version had no visible path at all from "I have a
     * configuration" to "it is running", and every button looked equally like the next thing to press.
     */
    private JPanel buildSteps()
    {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        // --- step 1: which configuration ---
        stepChoose.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(stepChoose);

        initialize.addActionListener(e -> initialize());
        panel.add(leftAligned(styled(initialize, true)));

        JPanel choose = row();

        configurations.setMaximumRowCount(12);
        configurations.setPreferredSize(new Dimension(190, 24));
        choose.add(styled(configurations, false));

        // Everything that manages configurations lives behind one button and the combo's own context
        // menu, rather than as five peers competing with the step that matters.  They are all rare:
        // a configuration is made once and chosen thereafter.
        final JButton manage = new JButton(I18n.t("autosetup.ui.btnManage"));
        manage.addActionListener(e -> manageMenu().show(manage, 0, manage.getHeight()));
        choose.add(styled(manage, false));

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

        panel.add(leftAligned(choose));

        // --- which pages count ---
        // On the surface rather than on the Manage menu: it decides which track is considered at all,
        // so it is the first thing to reach for when the list below is full of findings about a page
        // that is not part of the railway being automated.
        stepPages.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(stepPages);

        JPanel pageRow = row();

        JButton pages = new JButton(I18n.t("autosetup.ui.btnExcludePage"));
        pages.addActionListener(e -> choosePages());
        pageRow.add(styled(pages, true));

        pagesSummary.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
        pageRow.add(styled(pagesSummary, false));

        panel.add(leftAligned(pageRow));

        // --- step 2: turn it on ---
        stepEnable.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(stepEnable);

        enable.setToolTipText(I18n.t("autosetup.ui.tooltipLoadConfiguration"));
        enable.addActionListener(e ->
        {
            Object selected = configurations.getSelectedItem();

            if (selected != null) load(String.valueOf(selected), true);
        });

        panel.add(leftAligned(styled(enable, true)));

        status.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        panel.add(leftAligned(styled(status, false)));

        // --- step 3: run ---
        stepRun.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(stepRun);

        JPanel run = row();

        // Sends the user where placing actually happens.  That tab does not exist until a configuration
        // has loaded, which is precisely why the feature looked missing.
        placeLocomotives.addActionListener(e -> ui.showAutonomyRunTab());
        run.add(styled(placeLocomotives, false));

        if (ui.getModel() != null && ui.getModel().isDebug())
        {
            JButton inspect = new JButton(I18n.t("autosetup.ui.btnInspectGraph"));
            inspect.addActionListener(e -> inspect());
            run.add(styled(inspect, false));
        }

        panel.add(leftAligned(run));

        return panel;
    }

    private JPanel leftAligned(java.awt.Component component)
    {
        JPanel holder = row();
        holder.setAlignmentX(LEFT_ALIGNMENT);
        holder.add(component);
        return holder;
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
        menu.add(item(I18n.t("autosetup.ui.btnCheckConfiguration"), new Runnable()
            { public void run() { recheck(); } }));

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
     */
    private JPanel buildFindings()
    {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new javax.swing.BoxLayout(heading, javax.swing.BoxLayout.Y_AXIS));

        JLabel title = step(I18n.t("autosetup.ui.headingFindings"));
        title.setAlignmentX(LEFT_ALIGNMENT);
        heading.add(title);

        hint.setFont(FONT_LIST);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        heading.add(hint);

        panel.add(heading, BorderLayout.NORTH);

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

                org.traincontrol.base.TileGraph.TileKey tile = findingRows.get(row);

                if (tile != null) ui.openAutonomyEditor(tile);
            }
        });

        // The findings are the only thing here that is genuinely wider than the panel, so this is the
        // only thing that scrolls sideways.  Everything else is pinned narrow (see setPanelWidth), so
        // the whole tab no longer slides left and right because one sentence was long.
        JScrollPane scroll = new JScrollPane(findings,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(java.awt.Color.WHITE);
        scroll.setPreferredSize(new Dimension(PANEL_WIDTH, 260));

        panel.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 2));
        bottom.setOpaque(false);

        // A navy label rather than a titled border: there is not one titled border anywhere else in
        // this window, and a box around a list is a second frame inside a frame.
        bottom.add(step(I18n.t("autosetup.ui.labelLocomotiveRoster")), BorderLayout.NORTH);

        JList<String> locomotives = new JList<>(roster);
        locomotives.setFont(FONT_LIST);

        JScrollPane rosterScroll = new JScrollPane(locomotives,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        rosterScroll.setBorder(BorderFactory.createEmptyBorder());
        rosterScroll.setPreferredSize(new Dimension(PANEL_WIDTH, 90));
        rosterScroll.getViewport().setBackground(java.awt.Color.WHITE);

        bottom.add(rosterScroll, BorderLayout.CENTER);

        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
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
     * Re-runs the checks and says so.
     *
     * Says so even when nothing changed: pressing a button that silently does the same thing again
     * reads as a button that does not work.
     */
    private void recheck()
    {
        refresh();

        // into the hint above the list, not the status line: that one says which configuration is
        // running, and a check result overwriting it would lose the more important fact
        hint.setVisible(true);
        hint.setText(findingsModel.isEmpty()
            ? I18n.t("autosetup.ui.labelCheckedClean")
            : I18n.f("autosetup.ui.labelCheckedNow", countFindings()));
    }

    /**
     * Chooses which pages autonomy uses.
     *
     * Here rather than in the editor because it is a property of the whole setup, and because the
     * commonest reason to reach for it - a page full of findings that is not part of the railway being
     * automated - is discovered while reading this list.
     */
    private void choosePages()
    {
        java.util.List<org.traincontrol.base.LayoutDiagram> pages = session.getPages();

        JPanel panel = new JPanel(new GridLayout(0, 1, 2, 2));
        panel.add(styled(new JLabel(I18n.t("autosetup.ui.promptExcludePage")), false));

        java.util.Map<String, javax.swing.JCheckBox> boxes = new java.util.LinkedHashMap<>();

        for (org.traincontrol.base.LayoutDiagram page : pages)
        {
            boolean excluded = session.getStore().getExcludedPages().contains(page.getName());

            javax.swing.JCheckBox box = new javax.swing.JCheckBox(page.getName(), !excluded);
            boxes.put(page.getName(), box);
            panel.add(styled(box, false));
        }

        if (JOptionPane.showConfirmDialog(this, new JScrollPane(panel),
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
    private void load(String name, boolean interactive)
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
                JOptionPane.showMessageDialog(this,
                    I18n.f("autosetup.ui.errorCannotBuildDetail", countBlocking()));
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
            if (interactive) JOptionPane.showMessageDialog(this, String.valueOf(e.getMessage()));
            else if (ui.getModel() != null) ui.getModel().log(String.valueOf(e.getMessage()));

            revert(previous);
        }

        refresh();
    }

    private int countBlocking()
    {
        int blocking = 0;

        for (org.traincontrol.base.TileGraph.Problem problem : session.getGraph().getProblems())
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
    private void initialize()
    {
        String name = JOptionPane.showInputDialog(this,
            I18n.t("autosetup.ui.promptConfigurationName"));

        if (name == null || name.trim().isEmpty()) return;

        try
        {
            session.initialize(name.trim());
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(this, String.valueOf(e.getMessage()));
        }

        refresh();
    }

    /**
     * Reads an exported configuration file in as a new named configuration.
     *
     * The file is the store's own format, so what one person exports another can import onto the same
     * track - placements and settings travel, the track itself stays derived from each side's diagram.
     */
    private void importConfiguration()
    {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();

        if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;

        String name = JOptionPane.showInputDialog(this, I18n.t("autosetup.ui.promptImportName"),
            chooser.getSelectedFile().getName().replaceAll("\\.json$", ""));

        if (name == null || name.trim().isEmpty()) return;

        // importing over an existing name replaces it, which is sometimes wanted and never silent
        if (session.getStore().getConfigurationNames().contains(name.trim()))
        {
            int replace = JOptionPane.showConfirmDialog(this,
                I18n.f("autosetup.ui.confirmImportOverwrites", name.trim()),
                I18n.t("autosetup.ui.title"), JOptionPane.YES_NO_OPTION);

            if (replace != JOptionPane.YES_OPTION) return;
        }

        try
        {
            byte[] bytes = java.nio.file.Files.readAllBytes(chooser.getSelectedFile().toPath());

            session.getStore().importConfiguration(name.trim(),
                new org.json.JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));

            save();
        }
        catch (IOException | RuntimeException e)
        {
            JOptionPane.showMessageDialog(this,
                I18n.f("autosetup.ui.errorImportUnreadable", String.valueOf(e.getMessage())));
        }

        refresh();
    }

    /**
     * Writes the active configuration out where the user chooses, for another machine to import.
     */
    private void exportConfiguration()
    {
        String name = session.getStore().getActiveConfiguration();

        if (name == null) return;

        org.json.JSONObject configuration = session.getStore().getConfiguration(name);

        if (configuration == null) return;

        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setSelectedFile(new java.io.File(name + ".json"));

        if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;

        try
        {
            java.nio.file.Files.write(chooser.getSelectedFile().toPath(),
                configuration.toString(4).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(this, String.valueOf(e.getMessage()));
        }
    }

    private void duplicate()
    {
        String from = session.getStore().getActiveConfiguration();

        String name = JOptionPane.showInputDialog(this,
            I18n.t("autosetup.ui.promptConfigurationName"));

        if (name == null || name.trim().isEmpty()) return;

        // as a copy, so a variant that differs only in where the locomotives start does not mean
        // re-entering every decision that has nothing to do with that
        session.getStore().createConfiguration(name.trim(), from);
        session.getStore().setActiveConfiguration(name.trim());

        save();
        refresh();
    }

    private void rename()
    {
        String from = session.getStore().getActiveConfiguration();

        if (from == null) return;

        String name = JOptionPane.showInputDialog(this,
            I18n.t("autosetup.ui.promptConfigurationName"), from);

        if (name == null || name.trim().isEmpty()) return;

        try
        {
            session.getStore().renameConfiguration(from, name.trim());
            save();
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(this, String.valueOf(e.getMessage()));
        }

        refresh();
    }

    private void delete()
    {
        String name = session.getStore().getActiveConfiguration();

        if (name == null) return;

        try
        {
            session.getStore().deleteConfiguration(name);
            save();
        }
        catch (IOException e)
        {
            // the last configuration cannot go: a setup with none is a state nothing here could act on
            JOptionPane.showMessageDialog(this, I18n.t("autosetup.ui.errorLastConfiguration"));
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
            JOptionPane.showMessageDialog(this, String.valueOf(e.getMessage()));
        }
    }

    /**
     * Writes the derived graph out in the ordinary autonomy format, laid out like the track it came
     * from, so it can be read and checked against the diagram beside it.
     */
    private void inspect()
    {
        try
        {
            java.io.File out = new java.io.File("autonomy-derived.json");

            java.nio.file.Files.write(out.toPath(),
                session.buildConfigurationForInspection().getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));

            JOptionPane.showMessageDialog(this, out.getAbsolutePath());
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(this, String.valueOf(e.getMessage()));
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
        for (org.traincontrol.base.TileGraph.Problem problem : session.getGraph() == null
            ? java.util.Collections.<org.traincontrol.base.TileGraph.Problem>emptyList()
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
    private String describeTile(org.traincontrol.base.TileGraph.TileKey tile)
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
            org.traincontrol.base.TileGraph.TileKey tile =
                (org.traincontrol.base.TileGraph.TileKey) row[0];

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
                findingRows.add((org.traincontrol.base.TileGraph.TileKey) row[0]);
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

    /**
     * How many rows are actual findings rather than headings.
     */
    private int countFindings()
    {
        int count = 0;

        for (AutonomyChecks.Severity severity : findingSeverity)
        {
            if (severity != null) count++;
        }

        return count;
    }
}
