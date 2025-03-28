package org.traincontrol.gui;

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
        
        if (component != null)
        {
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

            menuItem = new JMenuItem("Paste");
            menuItem.addActionListener(event -> 
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
            menuItem.setToolTipText("Control+V");

            if (!edit.hasToolFlag()) menuItem.setEnabled(false);

            add(menuItem);

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
                menuItem = new JMenuItem("Edit Address");
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
            
            addSeparator();
        }
        
        
        menuItem = new JMenuItem("Increase Size");
        menuItem.addActionListener(event -> 
        {
            try
            {
                edit.addRowsAndColumns(1);
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });
        menuItem.setToolTipText("Control+I");
        add(menuItem);
        
        menuItem = new JMenuItem("Clear");
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
   