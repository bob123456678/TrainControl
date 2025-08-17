package org.traincontrol.gui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * This class represents a right-click menu with various utility functions for locomotive mapping pages
 * @author Adam
 */
public class RightClickPageMenu extends MouseAdapter
{    
    protected TrainControlUI ui;
    
    public RightClickPageMenu(TrainControlUI u)
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
        RightClickMenu menu = new RightClickMenu(ui, e);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }
    
    final class RightClickMenu extends JPopupMenu
    {
        JMenuItem menuItem;

        public RightClickMenu(TrainControlUI ui, MouseEvent e)
        {
            menuItem = new JMenuItem("Rename Page " + ui.getLocMappingNumber());
            menuItem.addActionListener(event -> ui.renameCurrentPage());    
            add(menuItem);
            
            addSeparator();

            menuItem = new JMenuItem("Copy Mappings");
            menuItem.addActionListener(event -> ui.copyCurrentPage());    
            add(menuItem);
            
            menuItem = new JMenuItem("Paste Mappings");
            
            if (ui.pageCopied())
            {
                menuItem.addActionListener(event -> ui.pasteCopiedPage());    
            }
            else
            {
                menuItem.setEnabled(false);
            }
            
            add(menuItem);
            
            addSeparator();
            
            menuItem = new JMenuItem("Map Unassigned Locomotives");
            menuItem.addActionListener(event -> ui.mapUnassignedLocomotives());
            menuItem.setToolTipText("Finds locomotives not mapped to any key, and maps them to free keys on this page.");
            add(menuItem);
            
            addSeparator();
            
            menuItem = new JMenuItem("Reset Current Mappings");
            menuItem.addActionListener(event -> ui.clearCurrentPage());    
            add(menuItem);
        }
    }
}
   