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
import javax.swing.JCheckBox;
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
 * Running a layout and watching it, from beside the diagram it runs on.
 *
 * The counterpart to the editor panel: that one is for deciding how the railway is wired, this one is
 * for using it.  Which configuration is loaded, what is switched on, where the locomotives are, and
 * whether anything looks wrong before trusting trains to it.
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

    private final JCheckBox layerMonitoring =
        new JCheckBox(I18n.t("autosetup.ui.layerMonitoring"), true);
    private final JCheckBox layerLabels = new JCheckBox(I18n.t("autosetup.ui.layerLabels"), true);
    private final JCheckBox layerLocomotives =
        new JCheckBox(I18n.t("autosetup.ui.layerLocomotives"), true);
    private final JCheckBox layerHomes = new JCheckBox(I18n.t("autosetup.ui.layerHomes"), false);

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

        panel.add(layerMonitoring);
        panel.add(layerLabels);
        panel.add(layerLocomotives);
        panel.add(layerHomes);

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
    private void load(String name)
    {
        if (isRunning())
        {
            JOptionPane.showMessageDialog(this, I18n.t("autosetup.ui.errorAutonomyRunning"));
            refresh();
            return;
        }

        session.getStore().setActiveConfiguration(name);
        session.rebuild();

        if (session.hasBlockingProblems())
        {
            JOptionPane.showMessageDialog(this, I18n.t("autosetup.ui.errorCannotBuild"));
            refresh();
            return;
        }

        try
        {
            ui.getModel().parseAuto(session.buildConfiguration());
        }
        catch (RuntimeException e)
        {
            JOptionPane.showMessageDialog(this, String.valueOf(e.getMessage()));
        }

        refresh();
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

    public boolean isShowingMonitoring()
    {
        return layerMonitoring.isSelected();
    }

    public boolean isShowingLabels()
    {
        return layerLabels.isSelected();
    }

    public boolean isShowingLocomotives()
    {
        return layerLocomotives.isSelected();
    }

    public boolean isShowingHomes()
    {
        return layerHomes.isSelected();
    }

    private boolean isRunning()
    {
        Layout layout = ui.getModel() == null ? null : ui.getModel().getAutoLayout();

        return layout != null && layout.isRunning();
    }

    /**
     * Re-reads everything and shows it.
     */
    public final void refresh()
    {
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
