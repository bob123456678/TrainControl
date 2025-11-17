package org.traincontrol.gui;

import java.awt.Font;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinLayoutComponent;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;

/**
 * This class represents a right-click menu on the track editor
 * @author Adam
 */
final class LayoutEditorRightclickMenu extends JPopupMenu
{        
    public LayoutEditorRightclickMenu(LayoutEditor edit, TrainControlUI ui, LayoutLabel label, MarklinLayoutComponent component)
    {        
        JMenuItem menuItem;
        
        // Show the name of the component
        if (component != null)
        {
            JMenuItem titleItem = new JMenuItem(component.getUserFriendlyTypeName());
            titleItem.setEnabled(false);
            titleItem.setFont(titleItem.getFont().deriveFont(Font.BOLD));
            add(titleItem);
            addSeparator();
        }
        
        JMenu pasteSubMenu = new JMenu(I18n.t("ui.paste")); // Create the submenu

        JMenuItem pasteMenuItem = new JMenuItem(I18n.t("layout.ui.tile"));
        pasteMenuItem.addActionListener(event -> 
        {
            try
            {
                edit.executeTool(label, null);
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });
        pasteMenuItem.setToolTipText("Control+V");

        pasteSubMenu.add(pasteMenuItem);

        JMenuItem pasteColumnMenuItem = new JMenuItem(I18n.t("layout.ui.entireCol"));
        pasteColumnMenuItem.addActionListener(event -> 
        {
            try
            {
                edit.executeTool(label, LayoutEditor.bulk.COL);
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

        JMenuItem pasteRowMenuItem = new JMenuItem(I18n.t("layout.ui.entireRow"));
        pasteRowMenuItem.addActionListener(event -> 
        {
            try
            {
                edit.executeTool(label, LayoutEditor.bulk.ROW);
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
        
        menuItem = new JMenuItem(I18n.t("ui.undo"));
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
        
        menuItem = new JMenuItem(I18n.t("ui.redo"));
        menuItem.addActionListener(event -> 
        {
            try
            {
                edit.redo();
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });
        
        if (!edit.canRedo()) menuItem.setEnabled(false);
        menuItem.setToolTipText("Control+Y");
        
        add(menuItem);
        
        if (component != null)
        {
            addSeparator();
            
            menuItem = new JMenuItem(I18n.t("ui.cut"));
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

            menuItem = new JMenuItem(I18n.t("ui.copy"));
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
            
            // Text can't be rotated
            if (!component.isText()
                // These elements are symmetrical
                && component.getNumOrientations() > 1
            )
            {
                menuItem = new JMenuItem(I18n.t("ui.rotate"));
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
            }

            add(menuItem);
                        
            if (component.isClickable())
            {
                addSeparator();
                
                String protocol = "";
    
                if (component.getProtocol() != null)
                {
                    protocol = MarklinAccessory.getProtocolStringForName(component.getProtocol().toString());
                }
                
                String addressLabel = I18n.t("layout.ui.address"); // component.getUserFriendlyTypeName() + " Address";
                
                if (component.isLink())
                {
                    addressLabel = I18n.t("layout.ui.addressLabelLinkedPage");
                }
                else if (component.isRoute())
                {
                    addressLabel = I18n.t("layout.ui.addressLabelRouteId");
                }
                else if (component.isFeedback())
                {
                    addressLabel = I18n.t("layout.ui.addressLabelFeedbackAddress");
                }

                menuItem = new JMenuItem(
                    I18n.f(
                        "layout.ui.menuEditAddressLabel",
                        addressLabel,
                        component.getLogicalAddress(),
                        protocol
                    )
                );

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
                
                // Shortcut to edit routes
                if (component.isRoute())
                {
                    // Get the route by address, otherwise it will not change as we edit
                    MarklinRoute route = ui.getModel().getRoute(component.getAddress());
                    
                    if (route != null)
                    {         
                        menuItem = new JMenuItem(I18n.t("layout.ui.openInRouteEditor"));
                        menuItem.addActionListener(event -> 
                        {
                            try
                            {
                                javax.swing.SwingUtilities.invokeLater(new Thread(() -> 
                                {
                                    ui.editRoute(route.getName());
                                }));
                            }
                            catch (Exception e)
                            {
                                JOptionPane.showMessageDialog(this, e.getMessage());
                            }
                        });
                        
                        menuItem.setToolTipText(
                            I18n.f("layout.ui.tooltip.shortcutEditRoute", route.getName())
                        );                        
                        add(menuItem);
                    }
                }
            }  

            addSeparator();

            menuItem = new JMenuItem(I18n.t("layout.ui.editTextLabel"));
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
            
            if (ui.getModel().hasAutoLayout() && !ui.getModel().getAutoLayout().getPoints().isEmpty())
            {     
                menuItem = new JMenuItem(I18n.t("layout.ui.placeAutoStationLabel"));
                menuItem.addActionListener(event -> 
                {
                    try
                    {
                        edit.editTextWithDropdown(label);
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(this, e.getMessage());
                    }
                });
                menuItem.setToolTipText("Control+S");
                add(menuItem);
            }

            addSeparator();
            
            menuItem = new JMenuItem(I18n.t("ui.delete"));
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
        }
        
        addSeparator();
        
        JMenu diagramSubmenu = new JMenu(
            I18n.t("layout.ui.menuDiagram")
        ); // Create the submenu

        menuItem = new JMenuItem(
            I18n.f(
                "layout.ui.menuIncreaseSize",
                edit.getMarklinLayout().getSx(),
                edit.getMarklinLayout().getSy()
            )
        );

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
        diagramSubmenu.add(menuItem);
        
        diagramSubmenu.addSeparator();
        
        menuItem = new JMenuItem(I18n.t("layout.ui.shiftRight"));
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
        
        menuItem.setToolTipText(
            I18n.t("layout.ui.tooltip.shiftDiagramRight")
        );
        diagramSubmenu.add(menuItem);
                
        menuItem = new JMenuItem(I18n.t("layout.ui.shiftDown"));
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
        
        menuItem.setToolTipText(
            I18n.t("layout.ui.tooltip.shiftDiagramDown")
        );
        diagramSubmenu.add(menuItem);
        
        menuItem = new JMenuItem(I18n.t("layout.ui.shiftLeft"));
        menuItem.addActionListener(event -> 
        {
            try
            {
                edit.shiftLeft();
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });
        
        menuItem.setToolTipText(
            I18n.t("layout.ui.tooltip.shiftDiagramLeft")
        );
        diagramSubmenu.add(menuItem);
        
        menuItem = new JMenuItem(I18n.t("layout.ui.shiftUp"));
        menuItem.addActionListener(event -> 
        {
            try
            {
                edit.shiftUp();
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });
        
        menuItem.setToolTipText(
            I18n.t("layout.ui.tooltip.shiftDiagramUp")
        );
        diagramSubmenu.add(menuItem);
        
        diagramSubmenu.addSeparator();
        
        menuItem = new JMenuItem(I18n.t("layout.ui.clearDiagram"));
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
        
        diagramSubmenu.add(menuItem);
        
        add(diagramSubmenu);
    }
}
   