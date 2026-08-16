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

    private final JButton initialize = new JButton(I18n.t("autosetup.ui.btnInitFromLayout"));
    private final JButton load = new JButton(I18n.t("autosetup.ui.btnLoadConfiguration"));

    private final JLabel status = new JLabel();

    // Set while a configuration is being loaded into the combo, so reacting to that does not load it
    // straight back again
    private boolean populating = false;

    public AutonomyViewerPanel(AutonomySession session, TrainControlUI ui)
    {
        this.session = session;
        this.ui = ui;

        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        add(buildTop(), BorderLayout.NORTH);
        add(buildMiddle(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        refresh();
    }

    /**
     * The window's own font, one size down - what the rest of the application uses for controls.
     */
    static final java.awt.Font FONT = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12);

    static final java.awt.Font FONT_BOLD = new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12);

    /**
     * Applies the application's control font to a component, and returns it.
     */
    static <T extends javax.swing.JComponent> T styled(T component, boolean bold)
    {
        component.setFont(bold ? FONT_BOLD : FONT);
        return component;
    }

    private JPanel buildTop()
    {
        JPanel panel = new JPanel(new GridLayout(0, 1, 2, 2));

        panel.add(styled(new JLabel(I18n.t("autosetup.ui.labelConfiguration")), true));

        // The starting point for a layout that has no setup yet.  Everything else on the panel is about
        // configurations, and until this is pressed there are none.
        initialize.addActionListener(e -> initialize());
        panel.add(styled(initialize, false));

        // Choosing a configuration only SELECTS it.  Loading is a button, because loading rebuilds the
        // graph and stops whatever is running - too much to happen because a list scrolled past an
        // entry, and impossible to reach at all when there is only one configuration to pick from.
        panel.add(styled(configurations, false));

        load.setToolTipText(I18n.t("autosetup.ui.tooltipLoadConfiguration"));
        load.addActionListener(e ->
        {
            Object selected = configurations.getSelectedItem();

            if (selected != null) load(String.valueOf(selected), true);
        });

        panel.add(styled(load, true));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));

        JButton duplicate = new JButton(I18n.t("autosetup.ui.menuNewConfiguration"));
        duplicate.addActionListener(e -> duplicate());
        buttons.add(styled(duplicate, false));

        JButton rename = new JButton(I18n.t("autosetup.ui.menuRenameConfiguration"));
        rename.addActionListener(e -> rename());
        buttons.add(styled(rename, false));

        JButton delete = new JButton(I18n.t("autosetup.ui.menuDeleteConfiguration"));
        delete.addActionListener(e -> delete());
        buttons.add(styled(delete, false));

        panel.add(buttons);

        JPanel transfer = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));

        JButton importButton = new JButton(I18n.t("autosetup.ui.btnImportConfiguration"));
        importButton.addActionListener(e -> importConfiguration());
        transfer.add(styled(importButton, false));

        JButton exportButton = new JButton(I18n.t("autosetup.ui.btnExportConfiguration"));
        exportButton.addActionListener(e -> exportConfiguration());
        transfer.add(styled(exportButton, false));

        JButton pages = new JButton(I18n.t("autosetup.ui.btnExcludePage"));
        pages.addActionListener(e -> choosePages());
        transfer.add(styled(pages, false));

        panel.add(transfer);

        return panel;
    }

    private JPanel buildMiddle()
    {
        JPanel panel = new JPanel(new GridLayout(2, 1, 4, 4));

        JList<String> locomotives = new JList<>(roster);
        JScrollPane rosterScroll = new JScrollPane(styled(locomotives, false));
        rosterScroll.setBorder(BorderFactory.createTitledBorder(
            I18n.t("autosetup.ui.labelLocomotiveRoster")));
        panel.add(rosterScroll);

        JScrollPane findingsScroll = new JScrollPane(styled(findings, false));
        findingsScroll.setBorder(BorderFactory.createTitledBorder(
            I18n.t("autosetup.ui.colWarnings")));
        panel.add(findingsScroll);

        panel.setPreferredSize(new Dimension(260, 280));

        return panel;
    }

    private JPanel buildBottom()
    {
        JPanel panel = new JPanel(new GridLayout(0, 1, 2, 2));

        JButton check = new JButton(I18n.t("autosetup.ui.btnCheckConfiguration"));
        check.addActionListener(e -> recheck());
        panel.add(styled(check, false));

        // Reading the graph as a graph is a diagnostic, not part of setting autonomy up - the diagram
        // is the layout now.  Shown only in debug mode, where the old window is still worth having.
        if (ui.getModel() != null && ui.getModel().isDebug())
        {
            JButton inspect = new JButton(I18n.t("autosetup.ui.btnInspectGraph"));
            inspect.addActionListener(e -> inspect());
            panel.add(styled(inspect, false));
        }

        status.setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));
        panel.add(styled(status, false));

        return panel;
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

        status.setText(findingsModel.isEmpty()
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
            ui.autonomyLoadedFromDiagram(name);
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
        // Until a setup exists there is nothing for the rest of the panel to act on, so it offers the
        // one thing that can be done and nothing that cannot.
        boolean exists = session.exists() || !session.getStore().getConfigurationNames().isEmpty();

        initialize.setVisible(!exists);

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
     * The list of things to look at, grouped under the page each one is on.
     *
     * Grouped because on a multi-page layout most findings belong to pages the reader is not working
     * on, and a flat list makes them read every line to discover that.  A page heading also makes the
     * commonest fix visible: a page that is nothing but findings usually wants leaving out altogether.
     */
    private void refreshFindings()
    {
        findingsModel.clear();
        findingRows.clear();

        // page -> its findings, in the order the checks produced them
        Map<String, List<String>> byPage = new java.util.LinkedHashMap<>();
        Map<String, List<org.traincontrol.base.TileGraph.TileKey>> tilesByPage =
            new java.util.LinkedHashMap<>();

        for (org.traincontrol.base.TileGraph.Problem problem : session.getGraph() == null
            ? java.util.Collections.<org.traincontrol.base.TileGraph.Problem>emptyList()
            : session.getGraph().getProblems())
        {
            add(byPage, tilesByPage, problem.getTile(),
                describe(problem.getMessageKey(),
                    problem.getTile() == null ? "" : problem.getTile().toString()));
        }

        for (AutonomyChecks.Finding finding : session.check())
        {
            add(byPage, tilesByPage, finding.getTile(),
                describe(finding.getMessageKey(), finding.getSubject()));
        }

        // a page renumbered under the setup would silently reattach settings to the wrong track, so it
        // is said out loud rather than left in a getter nobody calls
        for (Map.Entry<String, String> entry : session.getStore().getPageIdConflicts().entrySet())
        {
            add(byPage, tilesByPage, null,
                I18n.f("autosetup.ui.warnPageRenumbered", entry.getKey(), entry.getValue()));
        }

        for (Map.Entry<String, List<String>> entry : byPage.entrySet())
        {
            findingsModel.addElement(I18n.f("autosetup.ui.labelPageHeading", entry.getKey()));
            findingRows.add(null);

            List<org.traincontrol.base.TileGraph.TileKey> tiles = tilesByPage.get(entry.getKey());

            for (int i = 0; i < entry.getValue().size(); i++)
            {
                findingsModel.addElement("   " + entry.getValue().get(i));
                findingRows.add(tiles.get(i));
            }
        }

        status.setText(session.getReducer() == null ? "" :
            I18n.f("autosetup.ui.labelGraphSize",
                session.getReducer().getPoints().size(),
                session.getReducer().getEdges().size()));
    }

    private void add(Map<String, List<String>> byPage,
        Map<String, List<org.traincontrol.base.TileGraph.TileKey>> tilesByPage,
        org.traincontrol.base.TileGraph.TileKey tile, String message)
    {
        String page = tile == null ? "" : tile.getPage();

        if (!byPage.containsKey(page))
        {
            byPage.put(page, new java.util.ArrayList<String>());
            tilesByPage.put(page, new java.util.ArrayList<org.traincontrol.base.TileGraph.TileKey>());
        }

        byPage.get(page).add(message);
        tilesByPage.get(page).add(tile);
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
     * How many rows are actual findings rather than page headings.
     */
    private int countFindings()
    {
        int count = 0;

        for (org.traincontrol.base.TileGraph.TileKey tile : findingRows)
        {
            if (tile != null || findingRows.isEmpty()) count++;
        }

        return count == 0 ? findingsModel.size() : count;
    }
}
