package org.traincontrol.gui;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import org.traincontrol.marklin.MarklinLayoutComponent;

/**
 * This class represents a right-click menu on the track diagram, to control autonomy
 * @author Adam
 */
final class LayoutEditorRightclickMenu extends JPopupMenu
{        
    public LayoutEditorRightclickMenu(LayoutEditor edit, TrainControlUI ui, LayoutLabel label, MarklinLayoutComponent component)
    {        
        JMenuItem menuItem;
        
        JMenu pasteSubMenu = new JMenu("Paste..."); // Create the submenu

        JMenuItem pasteMenuItem = new JMenuItem("Tile");
        pasteMenuItem.addActionListener(event -> 
        {
            try
            {
                edit.executeTool(label);
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });
        pasteMenuItem.setToolTipText("Control+V");

        pasteSubMenu.add(pasteMenuItem);

        JMenuItem pasteColumnMenuItem = new JMenuItem("Entire Column");
        pasteColumnMenuItem.addActionListener(event -> 
        {
            try
            {
                edit.setBulkColumn();
                edit.executeTool(label);
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });
        pasteColumnMenuItem.setToolTipText("Shift+C");
        
        if (edit.addBoxHighlighted())
        {
            pasteColumnMenuItem.setEnabled(false);
        }

        pasteSubMenu.add(pasteColumnMenuItem);

        JMenuItem pasteRowMenuItem = new JMenuItem("Entire Row");
        pasteRowMenuItem.addActionListener(event -> 
        {
            try
            {
                edit.setBulkRow();
                edit.executeTool(label);
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });
        pasteRowMenuItem.setToolTipText("Shift+R");

        if (edit.addBoxHighlighted())
        {
            pasteRowMenuItem.setEnabled(false);
        }
        
        if (!edit.hasToolFlag()) 
        {
            pasteSubMenu.setEnabled(false);
        }

        pasteSubMenu.add(pasteRowMenuItem); 

        add(pasteSubMenu); // Add the submenu to the parent menu
        
        menuItem = new JMenuItem("Undo");
        menuItem.addActionListener(event -> 
        {
            try
            {
                edit.undo();
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });
        
        if (!edit.canUndo()) menuItem.setEnabled(false);
        menuItem.setToolTipText("Control+Z");
        
        add(menuItem);
        
        if (component != null)
        {
            addSeparator();
            
            menuItem = new JMenuItem("Cut");
            menuItem.addActionListener(event -> 
            {
                try
                {
                    edit.initCopy(label, null, true);
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(this, e.getMessage());
                }
            });
            menuItem.setToolTipText("Control+X");

            add(menuItem);

            menuItem = new JMenuItem("Copy");
            menuItem.addActionListener(event -> 
            {
                try
                {
                    edit.initCopy(label, null, false);
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(this, e.getMessage());
                }
            });
            menuItem.setToolTipText("Control+C");

            add(menuItem);
            
            menuItem = new JMenuItem("Rotate");
            menuItem.addActionListener(event -> 
            {
                try
                {
                    edit.rotate(label);
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(this, e.getMessage());
                }
            });
            menuItem.setToolTipText("Control+R");

            add(menuItem);
            
            addSeparator();
            
            menuItem = new JMenuItem("Delete");
            menuItem.addActionListener(event -> 
            {
                try
                {
                    edit.delete(label);
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(this, e.getMessage());
                }
            });
            menuItem.setToolTipText("Delete");
            add(menuItem);

            addSeparator();

            menuItem = new JMenuItem("Edit Text");
            menuItem.addActionListener(event -> 
            {
                try
                {
                    edit.editText(label);
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog(this, e.getMessage());
                }
            });
            menuItem.setToolTipText("Control+T");

            add(menuItem);
            
            if (component.isClickable())
            {
                menuItem = new JMenuItem("Edit Address (" + component.getLogicalAddress() + ")");
                menuItem.addActionListener(event -> 
                {
                    try
                    {
                        edit.editAddress(label);
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(this, e.getMessage());
                    }
                });
                menuItem.setToolTipText("Control+A");

                add(menuItem);
            }            
        }
        
        addSeparator();
        
        /*menuItem = new JCheckBoxMenuItem("Show Text Labels");
        menuItem.setSelected(!edit.getMarklinLayout().getEditHideText());

        menuItem.addActionListener(event -> 
        {
            try
            {
                edit.toggleText();
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });
        menuItem.setToolTipText("Control+L");

        add(menuItem);

        menuItem = new JCheckBoxMenuItem("Show Address Labels");
        menuItem.setSelected(edit.getMarklinLayout().getEditShowAddress());

        menuItem.addActionListener(event -> 
        {
            try
            {
                edit.toggleAddresses();
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });
        menuItem.setToolTipText("Control+D");

        add(menuItem);
        
        addSeparator(); */

        menuItem = new JMenuItem("Increase Diagram Dimensions");
        menuItem.addActionListener(event -> 
        {
            try
            {
                edit.addRowsAndColumns(1, 1);
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });
        menuItem.setToolTipText("Control+I");
        add(menuItem);
        
        addSeparator();
        
        menuItem = new JMenuItem("Shift Right");
        menuItem.addActionListener(event -> 
        {
            try
            {
                edit.shiftRight();
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });
        menuItem.setToolTipText("Shifts the entire diagram right from the highlighted column");
        add(menuItem);
        
        menuItem = new JMenuItem("Shift Down");
        menuItem.addActionListener(event -> 
        {
            try
            {
                edit.shiftDown();
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });
        menuItem.setToolTipText("Shifts the entire diagram down from the highlighted row");
        add(menuItem);
        
        addSeparator();
        
        menuItem = new JMenuItem("Clear Diagram");
        menuItem.addActionListener(event -> 
        {
            try
            {
                edit.clear();
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });
        add(menuItem);
    }
}
   