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
import marklin.MarklinLocomotive;

/**
 * This class represents a right-click menu with various utility functions displayed when any locomotive button is right-clicked
 * @author Adam
 */
public class RightClickMenuListener extends MouseAdapter
{    
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
    
    final class RightClickMenu extends JPopupMenu
    {
        JMenuItem menuItem;

        public RightClickMenu(TrainControlUI ui)
        {       
            // Select the active locomotive
            menuItem = new JMenuItem("Assign Locomotive");
            menuItem.addActionListener(event -> ui.selectLocomotiveActivated(source));    
            menuItem.setToolTipText("Control+A");

            add(menuItem);
            
            menuItem = new JMenuItem("Quick Find Locomotive");
            
            if (ui.getModel().getLocomotives().isEmpty())
            {
                menuItem.setEnabled(false);
            }
            else
            {
                menuItem.addActionListener(event -> ui.quickLocSearch()); 
                menuItem.setToolTipText("Control+F");
            }
            
            add(menuItem);
            
            addSeparator();
            
            // Option to copy
            if (ui.buttonHasLocomotive(source))
            {                
                menuItem = new JMenuItem("Copy " + ui.getButtonLocomotive(source).getName());
                menuItem.addActionListener(event -> ui.setCopyTarget(source, false));
                menuItem.setToolTipText("Control+C");
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
                menuItem.addActionListener(event -> ui.doPaste(source, false, false));
                menuItem.setToolTipText("Control+V");
                add(menuItem);
                
                menuItem = new JMenuItem("Move " + ui.getCopyTarget().getName());
                menuItem.addActionListener(event -> ui.doPaste(source, false, true));
                menuItem.setToolTipText("Control+B");
                add(menuItem);
                
                // Show swap menu if triggered on a different button than where the copy command was triggered
                if (ui.getSwapTarget() != null && ui.getButtonLocomotive(source) != null && !ui.getButtonLocomotive(source).getName().equals(ui.getCopyTarget().getName()))
                {
                    menuItem = new JMenuItem("Swap with " + ui.getCopyTarget().getName());
                    menuItem.addActionListener(event -> ui.doPaste(source, true, false));
                    menuItem.setToolTipText("Control+S");
                    add(menuItem);
                }
            }
            else
            {
                menuItem = new JMenuItem("Paste Locomotive");
                menuItem.setEnabled(false);
                add(menuItem);

            }
            
            if (ui.buttonHasLocomotive(source))
            {  
                addSeparator();
    
                menuItem = new JMenuItem("Copy to next page");
                menuItem.addActionListener(event -> ui.copyToNextPage(source));
                add(menuItem);
                
                menuItem = new JMenuItem("Copy to previous page");
                menuItem.addActionListener(event -> ui.copyToPrevPage(source));
                add(menuItem);
            }

            if (ui.buttonHasLocomotive(source))
            {
                addSeparator();
                
                menuItem = new JMenuItem("Apply Saved Function Preset");
                menuItem.addActionListener(event -> ui.applyPreferredFunctions(ui.getButtonLocomotive(source)));
                menuItem.setToolTipText("Alt-P");
                add(menuItem);
                
                menuItem = new JMenuItem("Apply Saved Speed Preset (" + Integer.toString(ui.getButtonLocomotive(source).getPreferredSpeed()) + ")" ) ;
                menuItem.addActionListener(event -> ui.applyPreferredSpeed(ui.getButtonLocomotive(source)));
                menuItem.setToolTipText("Alt-V");
                add(menuItem);

                addSeparator();
                
                menuItem = new JMenuItem("Save Current Functions as Preset");
                menuItem.addActionListener(event -> ui.savePreferredFunctions(ui.getButtonLocomotive(source)));
                menuItem.setToolTipText("Alt-S");
                add(menuItem);
                
                menuItem = new JMenuItem("Save Current Speed as Preset");
                menuItem.addActionListener(event -> ui.savePreferredSpeed(ui.getButtonLocomotive(source)));
                menuItem.setToolTipText("Alt-U");

                add(menuItem);
                
                // Option to turn off functions and sync with station
                
                menuItem = new JMenuItem("Turn Off Functions");
                menuItem.addActionListener(event -> ui.locFunctionsOff(ui.getButtonLocomotive(source)));
                menuItem.setToolTipText("Alt-O");
                add(menuItem);

                menuItem = new JMenuItem("Sync w/ Central Station");
                menuItem.addActionListener(event -> ui.syncLocomotive(ui.getButtonLocomotive(source)));
                add(menuItem);
                
                addSeparator();

                menuItem = new JMenuItem("Set Local Locomotive Icon");
                menuItem.addActionListener(event -> ui.setLocIcon(ui.getButtonLocomotive(source)));
                
                add(menuItem);
                
                if (ui.getButtonLocomotive(source) != null && ui.getButtonLocomotive(source).getLocalImageURL() != null)
                {
                    menuItem = new JMenuItem("Clear Local Locomotive Icon");
                    menuItem.addActionListener(event -> ui.clearLocIcon(ui.getButtonLocomotive(source)));

                    add(menuItem);
                }
                
                menuItem = new JMenuItem("Customize Function Icons");
                menuItem.addActionListener(event -> ui.setFunctionIcon(ui.getButtonLocomotive(source), source));

                add(menuItem);
                     
                // Option to clear the mapping
                addSeparator();

                menuItem = new JMenuItem("Clear Button / Cut");
                menuItem.addActionListener(event -> {ui.setCopyTarget(source, true);});
                menuItem.setToolTipText("Control+X");
                add(menuItem);
                
                addSeparator();
                 
                menuItem = new JMenuItem("Edit Name/Address/Decoder");
                menuItem.addActionListener(event -> {ui.changeLocAddress((MarklinLocomotive) ui.getButtonLocomotive(source));});
                menuItem.setToolTipText("Control+R");
                add(menuItem);
                
                menuItem = new JMenuItem("Edit Notes");
                menuItem.addActionListener(event -> {ui.changeLocNotes(ui.getButtonLocomotive(source));});
                menuItem.setToolTipText("Control+N");
                add(menuItem);
                
                addSeparator();
                
                menuItem = new JMenuItem("Delete from Database");
                menuItem.setToolTipText("Control+Delete");
                menuItem.addActionListener(event -> { 
                    ui.deleteLoc(ui.getButtonLocomotive(source).getName());
                });
                
                add(menuItem);
            }
        }
    }
}
   