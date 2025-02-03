package org.traincontrol.gui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.traincontrol.marklin.MarklinRoute;

/**
 * This class represents a right-click menu with various utility functions displayed when any route entry is right-clicked
 * @author Adam
 */
public class RightClickRouteMenu extends MouseAdapter
{    
    protected TrainControlUI ui;
    
    public RightClickRouteMenu(TrainControlUI u)
    {
        this.ui = u;
    }
    
    @Override
    public void mousePressed(MouseEvent e)
    {
        if (e.isPopupTrigger()) showPopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e)
    {
        if (e.isPopupTrigger()) showPopup(e);
    }

    private void showPopup(MouseEvent e)
    {
        if (ui.getRouteAtCursor(e) != null)
        {
            RightClickMenu menu = new RightClickMenu(ui, e);
            menu.show(e.getComponent(), e.getX(), e.getY());
        }
    }
    
    final class RightClickMenu extends JPopupMenu
    {
        JMenuItem menuItem;

        public String getRouteTooltip(TrainControlUI ui, String route)
        {
            MarklinRoute currentRoute = ui.getModel().getRoute(route);
            return currentRoute.getName() + " (ID: " + ui.getRouteId(route) + ")";
        }
        
        public RightClickMenu(TrainControlUI ui, MouseEvent e)
        {       
            MarklinRoute route = ui.getRouteAtCursor(e);

            if (route != null)   
            {
                String routeName = route.getName();
            
                menuItem = new JMenuItem("Execute " + getRouteTooltip(ui, routeName));
                menuItem.addActionListener(event -> ui.executeRoute(routeName));    
                add(menuItem);
                addSeparator();

                menuItem = new JMenuItem(route.isLocked() ? "View Route Details" : "Edit Route");
                menuItem.addActionListener(event -> ui.editRoute(routeName));    
                add(menuItem);

                menuItem = new JMenuItem("Duplicate Route");
                menuItem.addActionListener(event -> ui.duplicateRoute(routeName));    
                add(menuItem);

                addSeparator();

                if (!route.isEnabled())
                {
                    menuItem = new JMenuItem("Enable Auto Execution");
                    menuItem.addActionListener(event -> ui.enableOrDisableRoute(routeName, true));    
                    add(menuItem);
                }
                else
                {
                    menuItem = new JMenuItem("Disable Auto Execution");
                    menuItem.addActionListener(event -> ui.enableOrDisableRoute(routeName, false)); 
                    add(menuItem);
                }
                
                if (!route.isLocked())
                {      
                    addSeparator();

                    menuItem = new JMenuItem("Change Route ID");
                    menuItem.addActionListener(event -> ui.changeRouteId(routeName));    
                    add(menuItem);

                    menuItem = new JMenuItem("Delete Route");
                    menuItem.addActionListener(event -> ui.deleteRoute(routeName));    
                    add(menuItem);
                }
            }
        }
    }
}
   