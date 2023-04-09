/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

/**
 * This class represents a right-click menu with various utility functions displayed when any locomotive button is right-clicked
 * @author Adam
 */
public class RightClickRouteMenu extends MouseAdapter {
    
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

        public RightClickMenu(TrainControlUI ui, MouseEvent e)
        {       
            String routeName = ui.getRouteAtCursor(e).toString();
            
            menuItem = new JMenuItem("Execute " + ui.getRouteTooltip(routeName));
            menuItem.addActionListener(event -> ui.executeRoute(routeName));    
            add(menuItem);
            addSeparator();
            
            menuItem = new JMenuItem("Edit Route");
            menuItem.addActionListener(event -> ui.editRoute(e));    
            add(menuItem);
            
            menuItem = new JMenuItem("Duplicate Route");
            menuItem.addActionListener(event -> ui.duplicateRoute(e));    
            add(menuItem);
            
            addSeparator();
            
            menuItem = new JMenuItem("Delete Route");
            menuItem.addActionListener(event -> ui.deleteRoute(e));    
            add(menuItem);
        }
    }
}
   