package org.traincontrol.gui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import static org.traincontrol.gui.TrainControlUI.HIDE_INACTIVE_PREF;
import static org.traincontrol.gui.TrainControlUI.HIDE_REVERSING_PREF;
import static org.traincontrol.gui.TrainControlUI.SHOW_STATION_LENGTH;
import org.traincontrol.util.I18n;

/**
 * This class represents a right-click menu when the graph UI is clicked outside of a point
 * @author Adam
 */
final class GraphRightClickGeneralMenu extends JPopupMenu
{
    public GraphRightClickGeneralMenu(TrainControlUI ui, int x, int y, boolean running, GraphViewer parent)
    {
        JMenuItem menuItem;
        
        if (!running)
        {
            menuItem = new JMenuItem(
                I18n.t("autolayout.ui.menuCreatePoint")
            );
            menuItem.addActionListener(event ->
            {
                String dialogResult = JOptionPane.showInputDialog(
                    (Component) parent.getSwingView(),
                    I18n.t("autolayout.ui.promptEnterPointName"),
                    ""
                );

                if (dialogResult != null && !"".equals(dialogResult))
                {
                    try
                    {
                        if (ui.getModel().getAutoLayout().getPoint(dialogResult) != null)
                        {
                            JOptionPane.showMessageDialog(
                                (Component) parent.getSwingView(),
                                I18n.t("autolayout.ui.errorPointNameInUse")
                            );
                        }
                        else
                        {
                            ui.getModel().getAutoLayout().createPoint(dialogResult, false, null);

                            Point p = ui.getModel().getAutoLayout().getPoint(dialogResult);

                            p.setX(x);
                            p.setY(y);

                            parent.getMainGraph().addNode(p.getUniqueId());
                            parent.getMainGraph().getNode(p.getUniqueId()).setAttribute("x", p.getX());
                            parent.getMainGraph().getNode(p.getUniqueId()).setAttribute("y", p.getY());
                            parent.getMainGraph().getNode(p.getUniqueId()).setAttribute("weight", 3);

                            ui.updatePoint(p, parent.getMainGraph());
                            ui.getModel().getAutoLayout().refreshUI();
                            ui.repaintAutoLocList(false);
                            parent.setLastClickedNode(p.getName());
                        }
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(
                            (Component) parent.getSwingView(),
                            I18n.t("autolayout.ui.errorAddNode")
                        );
                    }
                }
            });

            add(menuItem);
            addSeparator();

            menuItem = new JMenuItem(
                I18n.t("autolayout.ui.menuStartAutonomy")
            );
            menuItem.addActionListener(event ->
            {
                try
                {
                    ui.requestStartAutonomy();
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(
                        this,
                        e.getMessage()
                    );
                }
            });

            add(menuItem);
            addSeparator();

            menuItem = new JMenuItem(
                I18n.t("autolayout.ui.menuClearLocomotives")
            );
            menuItem.addActionListener(event ->
            {
                if (!ui.getModel().getAutoLayout().isRunning())
                {
                    try
                    {
                        int dialogResult = JOptionPane.showOptionDialog(
                            (Component) parent.getSwingView(),
                            I18n.t("autolayout.ui.confirmClearLocomotives"),
                            I18n.t("autolayout.ui.confirmDeletionTitle"),
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.PLAIN_MESSAGE,
                            null,
                            TrainControlUI.YES_NO_OPTS,
                            TrainControlUI.YES_NO_OPTS[0]
                        );

                        if (dialogResult == JOptionPane.YES_OPTION)
                        {
                            List<Locomotive> locs = new ArrayList<>(ui.getModel().getAutoLayout().getLocomotivesToRun());

                            for (Locomotive l : locs)
                            {
                                Point p = ui.getModel().getAutoLayout().getLocomotiveLocation(l);

                                if (p != null && !p.isReversing() && p.isDestination())
                                {
                                    ui.getModel().getAutoLayout().moveLocomotive(null, p.getName(), false);
                                    ui.updatePoint(p, parent.getMainGraph());
                                }
                            }

                            ui.repaintAutoLocList(false);
                        }
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(
                            this,
                            e.getMessage()
                        );
                    }
                }
            });

            add(menuItem);
        }
        else
        {
            menuItem = new JMenuItem(
                I18n.t("autolayout.ui.menuStopAutonomyGracefully")
            );
            menuItem.addActionListener(event ->
            {
                try
                {
                    ui.requestStopAutonomy();
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(
                        this,
                        e.getMessage()
                    );
                }
            });

            add(menuItem);
        }
        
        // Display options
        addSeparator();
        JMenu submenu = new JMenu(
            I18n.t("autolayout.ui.menuDisplayOptions")
        );
        
        if (!running)
        {
            JCheckBoxMenuItem hideRev = new JCheckBoxMenuItem(
                I18n.t("ui.main.hideReversingStations"), 
                TrainControlUI.getPrefs().getBoolean(HIDE_REVERSING_PREF, false)
            );
            hideRev.setToolTipText(I18n.t("ui.main.tooltip.hideReversingStations"));
            hideRev.addItemListener(event ->
            {
                try
                {
                    TrainControlUI.getPrefs().putBoolean(HIDE_REVERSING_PREF, 
                        !TrainControlUI.getPrefs().getBoolean(HIDE_REVERSING_PREF, false)
                    );
                    
                    ui.updateVisiblePoints();
                }
                catch (Exception ex)
                {
                    JOptionPane.showMessageDialog(parent, ex.getMessage());
                }
            });
           
            submenu.add(hideRev);
            
            JCheckBoxMenuItem hideInactive = new JCheckBoxMenuItem(
                I18n.t("ui.main.hideInactivePoints"), 
                TrainControlUI.getPrefs().getBoolean(HIDE_INACTIVE_PREF, false)
            );
            hideInactive.setToolTipText(I18n.t("ui.main.tooltip.hideInactivePoints"));
            hideInactive.addItemListener(event ->
            {
                try
                {
                    TrainControlUI.getPrefs().putBoolean(HIDE_INACTIVE_PREF, 
                        !TrainControlUI.getPrefs().getBoolean(HIDE_INACTIVE_PREF, false)
                    );
                    
                    ui.updateVisiblePoints();
                }
                catch (Exception ex)
                {
                    JOptionPane.showMessageDialog(parent, ex.getMessage());
                }
            });
           
            submenu.add(hideInactive);
            
            JCheckBoxMenuItem showLengths = new JCheckBoxMenuItem(
                I18n.t("ui.main.showLengthsExclusions"), 
                TrainControlUI.getPrefs().getBoolean(SHOW_STATION_LENGTH, true)
            );
            showLengths.setToolTipText(I18n.t("ui.main.tooltip.showLengthsExclusions"));
            showLengths.addItemListener(event ->
            {
                try
                {
                    TrainControlUI.getPrefs().putBoolean(SHOW_STATION_LENGTH, 
                        !TrainControlUI.getPrefs().getBoolean(SHOW_STATION_LENGTH, true)
                    );
                    
                    ui.updateVisiblePoints();
                }
                catch (Exception ex)
                {
                    JOptionPane.showMessageDialog(parent, ex.getMessage());
                }
            });
           
            submenu.add(showLengths);
        }
        else
        {
            submenu.setEnabled(false);
        }
        
        add(submenu);
    }
}
   