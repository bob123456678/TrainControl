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
    public static final int MAX_PATHS = 14;
    
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
                
                // Locomotive must not be running
                if (!ui.getModel().getAutoLayout().getActiveLocomotives().containsKey(locomotive))
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
                                    ui.ensureGraphUIVisible();

                                    boolean success = ui.getModel().getAutoLayout().executePath(
                                            path, locomotive, locomotive.getPreferredSpeed(), null
                                    );

                                    if (!success)
                                    {
                                        JOptionPane.showMessageDialog(this, "Auto route could not be executed: check log.");
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
   