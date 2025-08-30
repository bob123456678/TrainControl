package org.traincontrol.gui;

import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * This class represents a right-click menu with various utility functions displayed when any timetable entry is right-clicked
 * @author Adam
 */
public class RightClickTimetableMenu extends MouseAdapter
{    
    protected TrainControlUI ui;
    
    public RightClickTimetableMenu(TrainControlUI u)
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
        if (ui.getTimetableEntryAtCursor(e) != null)
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
            menuItem = new JMenuItem("Change Delay");
            menuItem.addActionListener(event -> ui.updateTimetableDelay(e));   
            menuItem.setToolTipText("Adjust the number of seconds before this route is started.");
            add(menuItem);
            
            menuItem = new JMenuItem("Delete Entry");
            menuItem.addActionListener(event -> ui.deleteTimetableEntry(e));   
            add(menuItem);
            
            addSeparator();
            
            menuItem = new JMenuItem("Restart Timetable");
            menuItem.addActionListener(event -> ui.restartTimetable());  
            menuItem.setToolTipText("This will reset progress and make the execution begin from the top.");
            add(menuItem);
            
            addSeparator();
            
            menuItem = new JMenuItem("Clear Timetable");
            menuItem.setForeground(Color.RED);
            menuItem.addActionListener(event -> ui.clearTimetable());  
            menuItem.setToolTipText("This will completely empty the timetable.");
            add(menuItem);
        }
    }
}
   