package org.traincontrol.gui;

import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * This class represents a right-click menu with various utility functions displayed when any locomotive DB tile is right-clicked
 * @author Adam
 */
public class RightClickSelectorMenu extends JPopupMenu
{
    JMenuItem menuItem;

    public RightClickSelectorMenu(TrainControlUI ui, MouseEvent e, Locomotive loc)
    {       
        menuItem = new JMenuItem(loc.getName());
        menuItem.setEnabled(false);
        add(menuItem);
        
        addSeparator();
        
        menuItem = new JMenuItem("Assign to button " + String.valueOf((char) ui.getKeyForCurrentButton().intValue()));
        menuItem.addActionListener(event -> {
            ui.mapLocToCurrentButton(loc.getName());
            ui.getLocSelector().refreshToolTips();
        });
        add(menuItem);   
        
        addSeparator();
        
        menuItem = new JMenuItem("Edit Name/Address/Decoder");
        menuItem.addActionListener(event -> {ui.changeLocAddress((MarklinLocomotive) loc, e);});
        add(menuItem);
        
        menuItem = new JMenuItem("Edit Notes");
        menuItem.addActionListener(event -> {ui.changeLocNotes(loc, e);});
        add(menuItem);
        
        addSeparator();
        
        menuItem = new JMenuItem("Delete from Database");
        menuItem.addActionListener(event -> { 
            ui.deleteLoc(loc.getName(), e);
        });    
        add(menuItem);
    }
}
   