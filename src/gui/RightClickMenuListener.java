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
import javax.swing.JPopupMenu;

/**
 * This class represents a right-click menu with various utility functions displayed when any locomotive button is right-clicked
 * @author Adam
 */
public class RightClickMenuListener extends MouseAdapter {
    
    protected TrainControlUI ui;
    protected JButton source;
    
    public RightClickMenuListener(TrainControlUI u, JButton source)
    {
        this.ui = u;
        this.source = source;
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
        RightClickMenu menu = new RightClickMenu(ui);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }
    
    class RightClickMenu extends JPopupMenu
    {
        JMenuItem menuItem;

        public RightClickMenu(TrainControlUI ui)
        {       
            // Option to copy
            if (ui.buttonHasLocomotive(source))
            {                
                menuItem = new JMenuItem("Copy " + ui.getButtonLocomotive(source).getName());
                menuItem.addActionListener(event -> ui.setCopyTarget(source) );    
            }
            else
            {
                menuItem = new JMenuItem("Copy Locomotive");
                menuItem.setEnabled(false);
            }
     
            add(menuItem);
            
            // Option to paste
            if (ui.hasCopyTarget())
            {
                menuItem = new JMenuItem("Paste " + ui.getCopyTarget().getName());

                menuItem.addActionListener(event -> ui.doPaste(source) );
            }
            else
            {
                menuItem = new JMenuItem("Paste Locomotive");
                menuItem.setEnabled(false);
            }

            add(menuItem);
            
            if (ui.buttonHasLocomotive(source))
            {  
                addSeparator();
    
                menuItem = new JMenuItem("Copy to next page");
                menuItem.addActionListener(event -> ui.copyToNextPage(source) );
                add(menuItem);
            }

            if (ui.buttonHasLocomotive(source))
            {
                // Option to turn off functions and sync with station
                addSeparator();
                
                menuItem = new JMenuItem("Turn Off Functions");

                menuItem.addActionListener(event -> ui.locFunctionsOff(ui.getButtonLocomotive(source)) );
                
                add(menuItem);

                menuItem = new JMenuItem("Sync w/ Central Station");

                menuItem.addActionListener(event -> ui.syncLocomotive(ui.getButtonLocomotive(source)) );
                
                add(menuItem);
                 
                // Option to clear the mapping
                addSeparator();

                menuItem = new JMenuItem("Clear Button");

                menuItem.addActionListener(event -> {ui.setCopyTarget(null);ui.doPaste(source);} );
                
                add(menuItem);
            }
        }
    }
}
   