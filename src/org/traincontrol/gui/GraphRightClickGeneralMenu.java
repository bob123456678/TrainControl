package org.traincontrol.gui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;

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
            menuItem = new JMenuItem("Create New Point");
            menuItem.addActionListener(event -> 
            {
                String dialogResult = JOptionPane.showInputDialog((Component) parent.getSwingView(), 
                    "Enter the new point name.",
                    "");

                if (dialogResult != null && !"".equals(dialogResult))
                {
                    try
                    {
                        if (ui.getModel().getAutoLayout().getPoint(dialogResult) != null)
                        {
                            JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                                "This point name is already in use.  Pick another.");
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
                        }
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                            "Error adding node.");
                    }
                }
            }); 

            add(menuItem);

            addSeparator();

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

            addSeparator();

            menuItem = new JMenuItem("Clear Locomotives from Graph");
            menuItem.addActionListener(event -> 
            {
                if (!ui.getModel().getAutoLayout().isRunning())
                {
                    try
                    {
                        int dialogResult = JOptionPane.showConfirmDialog(
                            (Component) parent.getSwingView(), "This will remove all locomotives from the graph \nexcept for those parked at reversing stations. Are you sure?" , "Confirm Deletion", JOptionPane.YES_NO_OPTION);

                        if(dialogResult == JOptionPane.YES_OPTION)
                        {
                            List<Locomotive> locs = new ArrayList<>(ui.getModel().getAutoLayout().getLocomotivesToRun());

                            for (Locomotive l: locs)
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
                        JOptionPane.showMessageDialog(this, e.getMessage());
                    }
                }
            });

            add(menuItem);
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
   