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

    private final JButton initialize = new JButton(I18n.t("autosetup.ui.btnInitFromLayout"));

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

    private JPanel buildTop()
    {
        JPanel panel = new JPanel(new GridLayout(0, 1, 2, 2));

        JLabel heading = new JLabel(I18n.t("autosetup.ui.labelConfiguration"));
        heading.setFont(heading.getFont().deriveFont(java.awt.Font.BOLD));
        panel.add(heading);

        // The starting point for a layout that has no setup yet.  Everything else on the panel is about
        // configurations, and until this is pressed there are none.
        initialize.addActionListener(e -> initialize());
        panel.add(initialize);

        // Choosing a configuration LOADS it, which is what makes it the one that runs next time too.
        // Refused while trains are moving, for the same reason any structural change is.
        configurations.addActionListener(e ->
        {
            if (populating) return;

            Object selected = configurations.getSelectedItem();

            if (selected != null) load(String.valueOf(selected));
        });

        panel.add(configurations);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));

        JButton duplicate = new JButton(I18n.t("autosetup.ui.menuNewConfiguration"));
        duplicate.addActionListener(e -> duplicate());
        buttons.add(duplicate);

        JButton rename = new JButton(I18n.t("autosetup.ui.menuRenameConfiguration"));
        rename.addActionListener(e -> rename());
        buttons.add(rename);

        JButton delete = new JButton(I18n.t("autosetup.ui.menuDeleteConfiguration"));
        delete.addActionListener(e -> delete());
        buttons.add(delete);

        panel.add(buttons);

        JPanel transfer = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));

        JButton importButton = new JButton(I18n.t("autosetup.ui.btnImportConfiguration"));
        importButton.addActionListener(e -> importConfiguration());
        transfer.add(importButton);

        JButton exportButton = new JButton(I18n.t("autosetup.ui.btnExportConfiguration"));
        exportButton.addActionListener(e -> exportConfiguration());
        transfer.add(exportButton);

        panel.add(transfer);

        return panel;
    }

    private JPanel buildMiddle()
    {
        JPanel panel = new JPanel(new GridLayout(2, 1, 4, 4));

        JList<String> locomotives = new JList<>(roster);
        JScrollPane rosterScroll = new JScrollPane(locomotives);
        rosterScroll.setBorder(BorderFactory.createTitledBorder(
            I18n.t("autosetup.ui.labelLocomotiveRoster")));
        panel.add(rosterScroll);

        JList<String> findings = new JList<>(findingsModel);
        JScrollPane findingsScroll = new JScrollPane(findings);
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
        check.addActionListener(e -> refresh());
        panel.add(check);

        JButton inspect = new JButton(I18n.t("autosetup.ui.btnInspectGraph"));
        inspect.addActionListener(e -> inspect());
        panel.add(inspect);

        status.setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));
        panel.add(status);

        return panel;
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

        if (active != null) load(active);
    }

    private void load(String name)
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
            JOptionPane.showMessageDialog(this, I18n.t("autosetup.ui.errorCannotBuild"));
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
            JOptionPane.showMessageDialog(this, String.valueOf(e.getMessage()));
            revert(previous);
        }

        refresh();
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

    private void refreshFindings()
    {
        findingsModel.clear();

        List<AutonomyChecks.Finding> found = session.check();

        for (AutonomyChecks.Finding finding : found)
        {
            String message;

            try
            {
                message = I18n.f(finding.getMessageKey(), finding.getSubject());
            }
            catch (RuntimeException e)
            {
                message = finding.getMessageKey() + " " + finding.getSubject();
            }

            findingsModel.addElement(message);
        }

        // a page renumbered under the setup would silently reattach settings to the wrong track, so it
        // is said out loud rather than left in a getter nobody calls
        Map<String, String> conflicts = session.getStore().getPageIdConflicts();

        for (Map.Entry<String, String> entry : conflicts.entrySet())
        {
            findingsModel.addElement(I18n.f("autosetup.ui.warnPageRenumbered",
                entry.getKey(), entry.getValue()));
        }

        status.setText(session.getReducer() == null ? "" :
            session.getReducer().getPoints().size() + " / " + session.getReducer().getEdges().size());
    }
}
