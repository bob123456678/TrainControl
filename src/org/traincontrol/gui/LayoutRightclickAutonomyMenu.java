package org.traincontrol.gui;

import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;

/**
 * This class represents a right-click menu on the track diagram, to control autonomy
 * @author Adam
 */
final class LayoutRightclickAutonomyMenu extends JPopupMenu
{    
    public static final int MAX_PATHS = 13;
    
    public LayoutRightclickAutonomyMenu(TrainControlUI ui, String stationName)
    {        
        JMenuItem menuItem;
                
        if (!ui.getModel().getAutoLayout().isAutoRunning())
        {
            menuItem = new JMenuItem("Start Autonomous Operation");
            menuItem.addActionListener(event -> 
            {
                try
                {
                    ui.requestStartAutonomy();
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(this, e.getMessage());
                }
            });

            add(menuItem);
            
            // Get the autonomy point corresponding to this station
            Point current = ui.getModel().getAutoLayout().getPoint(stationName);
            
            if (current != null && current.isDestination())
            {
                // Get the locomotive at this station
                Locomotive locomotive = current.getCurrentLocomotive();
                                
                // If we want to view paths, locomotive must not be running
                if (locomotive != null && !ui.getModel().getAutoLayout().getActiveLocomotives().containsKey(locomotive))
                {
                    List<List<Edge>> paths = ui.getModel().getAutoLayout().getPossiblePaths(locomotive, true);
                    
                    paths.sort((List<Edge> p1, List<Edge> p2) -> Edge.pathToString(p1).compareTo(Edge.pathToString(p2)));
                
                    if (!paths.isEmpty())
                    {
                        addSeparator();
                        
                        // Show the locomotive name for reference
                        menuItem = new JMenuItem(locomotive.getName());
                        menuItem.setEnabled(false);
                        add(menuItem);
                    }
                    
                    for (List<Edge> path : paths)
                    {
                        menuItem = new JMenuItem("-> " + path.get(path.size() - 1).getEnd().getName());
                        menuItem.addActionListener(event -> 
                        {
                            try
                            {
                                // TODO there is commonality with AutoLocomotiveStatus - reuse code
                                new Thread(() ->
                                {
                                    if (!ui.getModel().getPowerState())
                                    {
                                        JOptionPane.showMessageDialog(this, "To start autonomy, please turn the track power on, or cycle the power.");
                                    }
                                    else
                                    {               
                                        ui.ensureGraphUIVisible();

                                        boolean success = ui.getModel().getAutoLayout().executePath(
                                            path, locomotive, locomotive.getPreferredSpeed(), null
                                        );

                                        if (!success)
                                        {
                                            JOptionPane.showMessageDialog(this, "Auto route could not be executed: check log.");
                                        }
                                    }
                                }).start();
                            }
                            catch (Exception e)
                            {
                                JOptionPane.showMessageDialog(this, e.getMessage());
                            }
                        });    

                        add(menuItem);
                        
                        if (this.getComponentCount() > MAX_PATHS + 1)
                        {
                            menuItem = new JMenuItem("...");
                            menuItem.addActionListener(event -> 
                            {
                                try
                                {
                                    ui.jumpToAutonomyLocTab();
                                }
                                catch (Exception e)
                                {
                                    JOptionPane.showMessageDialog(this, e.getMessage());
                                }
                            });    

                            add(menuItem);
                            break;
                        }
                    }
                }
                
                addSeparator();

                // Station name label
                menuItem = new JMenuItem(stationName);
                menuItem.setEnabled(false);
                add(menuItem);
                
                // Place a different locomotive at this station
                if (ui.getActiveLoc() != null && !ui.getModel().getAutoLayout().isRunning() &&
                        !ui.getActiveLoc().equals(locomotive)
                )
                {
                    menuItem = new JMenuItem("Place " + ui.getActiveLoc().getName());
                    menuItem.addActionListener(event -> 
                    {
                        ui.getModel().getAutoLayout().moveLocomotive(ui.getActiveLoc().getName(), current.getName(), false);
                        ui.repaintAutoLocList(false);
                    });
                    
                    add(menuItem);
                }
                       
                if (current.getCurrentLocomotive() != null)
                {
                    menuItem = new JMenuItem("Remove " + current.getCurrentLocomotive().getName());
                    menuItem.addActionListener(event -> 
                    {
                        ui.getModel().getAutoLayout().moveLocomotive(null, current.getName(), false);
                        ui.repaintAutoLocList(false);
                    });

                    add(menuItem);
                }
            }
        }
        else
        {
            menuItem = new JMenuItem("Gracefully Stop Autonomy");
            menuItem.addActionListener(event -> 
            {
                try
                {
                    ui.requestStopAutonomy();
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(this, e.getMessage());
                }
            });    

            add(menuItem);
        }
    }
}
   