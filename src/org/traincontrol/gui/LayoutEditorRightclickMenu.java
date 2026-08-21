package org.traincontrol.gui;

import java.awt.Font;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.Route;
import org.traincontrol.util.I18n;

/**
 * This class represents a right-click menu on the track editor
 * @author Adam
 */
final class LayoutEditorRightclickMenu extends JPopupMenu
{        
    public LayoutEditorRightclickMenu(LayoutEditor edit, TrainControlUI ui, LayoutLabel label, LayoutDiagramComponent component)
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
        
        // Paste, singular.
        //
        // There used to be three of these - a tile, an entire row, an entire column - which is not
        // three ways to paste so much as three answers to a question the user was never asked.  The
        // row and column variants filled from the pasted tile to the edge of the diagram, so a
        // mis-aimed one wrote over a whole row of track and undo was the only way back.  Selecting the
        // squares and dragging them says the same thing, visibly, and can be corrected before it
        // happens rather than after.
        JMenuItem pasteMenuItem = new JMenuItem(I18n.t("ui.paste"));

        pasteMenuItem.addActionListener(event ->
        {
            try
            {
                // A group on the clipboard wins: it is the more recent and the more deliberate thing
                // the user did
                if (edit.hasGroupClipboard())
                {
                    edit.pasteSelection(edit.getGridX(label), edit.getGridY(label));
                }
                else
                {
                    edit.executeTool(label, null);
                }
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });

        pasteMenuItem.setToolTipText("Control+V");
        pasteMenuItem.setEnabled(edit.hasToolFlag() || edit.hasGroupClipboard());

        add(pasteMenuItem);

        // What can be done to a group of squares, gathered in one place so that the selection is a
        // thing the interface talks about rather than a hidden mode.
        //
        // Shown whether or not anything is picked, because this is also where a user finds out that
        // picking is possible - a menu that appears only once you have already worked out the gesture
        // is no use to the person who has not.
        {
            JMenu selectionMenu = new JMenu(
                I18n.f("layout.ui.menuSelection", edit.getSelection().size()));

            // Naming a row is exact and takes one click.  Dragging a box across sixty squares works
            // and is a small ordeal; on a wide diagram this is the one people will use.
            JMenuItem wholeRow = new JMenuItem(I18n.t("layout.ui.menuSelectRow"));
            wholeRow.addActionListener(event -> edit.selectRow(edit.getGridY(label)));
            selectionMenu.add(wholeRow);

            JMenuItem wholeColumn = new JMenuItem(I18n.t("layout.ui.menuSelectColumn"));
            wholeColumn.addActionListener(event -> edit.selectColumn(edit.getGridX(label)));
            selectionMenu.add(wholeColumn);

            JMenuItem everything = new JMenuItem(I18n.t("layout.ui.menuSelectAll"));
            everything.addActionListener(event -> edit.selectAll());
            selectionMenu.add(everything);


            // Only reachable on a grid square - the palette has no row to speak of
            boolean onDiagram = edit.getGridX(label) >= 0 && edit.getGridY(label) >= 0;

            wholeRow.setEnabled(onDiagram);
            wholeColumn.setEnabled(onDiagram);

            selectionMenu.addSeparator();

            boolean anyPicked = !edit.getSelection().isEmpty();

            JMenuItem copySelected = new JMenuItem(I18n.t("ui.copy"));
            copySelected.addActionListener(event -> edit.copySelection());
            copySelected.setToolTipText("Control+C");
            copySelected.setEnabled(anyPicked);
            selectionMenu.add(copySelected);

            // Cut, which the group had no way of doing.  Copy, paste, rotate, fill and delete were
            // all here; moving a run of track therefore meant copying it, pasting it, and going back
            // to delete the originals - three actions for one idea, and the third is the one that
            // gets forgotten and leaves a duplicate railway behind.
            JMenuItem cutSelected = new JMenuItem(I18n.t("ui.cut"));
            cutSelected.addActionListener(event -> edit.cutSelection());
            cutSelected.setToolTipText("Control+X");
            cutSelected.setEnabled(anyPicked);
            selectionMenu.add(cutSelected);

            JMenuItem fillSelected = new JMenuItem(I18n.t("layout.ui.menuFillSelection"));
            fillSelected.addActionListener(event -> edit.fillSelection());
            fillSelected.setEnabled(anyPicked && edit.hasToolFlag());
            selectionMenu.add(fillSelected);

            JMenuItem rotateSelected = new JMenuItem(I18n.t("ui.rotate"));
            rotateSelected.addActionListener(event -> edit.rotateSelection());
            rotateSelected.setEnabled(anyPicked);
            selectionMenu.add(rotateSelected);

            JMenuItem deleteSelected = new JMenuItem(I18n.t("ui.delete"));
            deleteSelected.addActionListener(event -> edit.deleteSelection());
            deleteSelected.setToolTipText("Delete");
            deleteSelected.setEnabled(anyPicked);
            selectionMenu.add(deleteSelected);

            selectionMenu.addSeparator();

            JMenuItem clearSelected = new JMenuItem(I18n.t("layout.ui.clearSelection"));
            clearSelected.addActionListener(event -> edit.clearSelection());
            clearSelected.setToolTipText("Escape");
            clearSelected.setEnabled(anyPicked);
            selectionMenu.add(clearSelected);

            selectionMenu.setToolTipText(I18n.t("layout.ui.tooltipSelection"));

            add(selectionMenu);

            // At the top level, not inside the submenu above.
            //
            // It is the way INTO picking, and it was filed under a menu called Selection - which is
            // where somebody looks once they already have a selection, not when they are trying to
            // make one.  The button that used to do this is gone, so this is now the only way in and
            // has to be where it will be found.
            //
            // Dragging cannot pick by default over track: a drag that begins on a tile has to go on
            // moving that tile, which is the older gesture and the one used constantly.  So this asks
            // for one box and then gets out of the way, rather than leaving a mode running.
            JMenuItem oneBox = new JMenuItem(I18n.t("layout.ui.menuSelectByDragging"));
            oneBox.addActionListener(event -> edit.selectOnce());
            oneBox.setToolTipText(I18n.t("layout.ui.tooltipSelectByDragging"));
            add(oneBox);
        }
        
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
                    protocol = Accessory.getProtocolStringForName(component.getProtocol().toString());
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
                    Route route = ui.getModel().getRoute(component.getAddress());
                    
                    if (route != null)
                    {         
                        menuItem = new JMenuItem(I18n.t("layout.ui.openInRouteEditor"));
                        menuItem.addActionListener(event -> 
                        {
                            try
                            {
                                javax.swing.SwingUtilities.invokeLater(() -> 
                                {
                                    ui.editRoute(route.getName());
                                });
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
            
            // Station labels are placed in the autonomy editor now, which is where the stations
            // themselves are decided.  This item wrote a "Point:" text label chosen from the LEGACY
            // graph's point names - a list that no longer describes the setup - so leaving it here
            // offered a second, worse way to do a thing the other window does properly.
            //
            // The Control+S shortcut it advertised is gone with it: an accelerator for a command that
            // is no longer on any menu is the half of a removal that gets left behind.

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

        // Bigger and smaller, as a matched pair.
        //
        // "+" adds a column on the right and a row at the bottom; "-" takes the same two away.  Being
        // exact mirrors is the point: a diagram grown by one press and shrunk by the next is the
        // diagram it started as, with every square still where it was.
        //
        // NOT a row at the top, which is what was asked for.  Inserting one moves every tile down, and
        // everything autonomy knows about a page is keyed by SQUARE - see LayoutEditor.growEdges.
        //
        // The four "shift the whole diagram" items are below, restored.  Each inserts a row or a
        // column at the hovered square and pushes everything past it along.  They were taken out when
        // multi-select arrived, on the reasoning that making room is now dragging what is in the way
        // out of it - which can be seen before it happens, and is still true, and is a different job.
        // Dragging moves the squares you picked; these move everything below or to the right of one
        // square, which on a diagram of two hundred tiles is not a selection anybody wants to make by
        // hand.  Each takes a snapshot first, so a mis-aimed one is a Control+Z away.
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
                edit.growEdges();
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });

        menuItem.setToolTipText("Control+I");
        diagramSubmenu.add(menuItem);

        menuItem = new JMenuItem(I18n.t("layout.ui.menuDecreaseSize"));

        menuItem.addActionListener(event ->
        {
            try
            {
                edit.shrinkEdges();
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });

        menuItem.setEnabled(edit.getMarklinLayout().edgesAreEmpty());
        diagramSubmenu.add(menuItem);

        diagramSubmenu.addSeparator();

        // In the order somebody reaches for them: down and right make room, up and left take it back.
        addShift(diagramSubmenu, "layout.ui.shiftDown", "layout.ui.tooltip.shiftDiagramDown",
            () -> edit.shiftDown());

        addShift(diagramSubmenu, "layout.ui.shiftRight", "layout.ui.tooltip.shiftDiagramRight",
            () -> edit.shiftRight());

        addShift(diagramSubmenu, "layout.ui.shiftUp", "layout.ui.tooltip.shiftDiagramUp",
            () -> edit.shiftUp());

        addShift(diagramSubmenu, "layout.ui.shiftLeft", "layout.ui.tooltip.shiftDiagramLeft",
            () -> edit.shiftLeft());

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

    /**
     * One of the four shift items.
     *
     * Written once rather than four times: they differ only in their words and in which method they
     * call, and four copies of the same try/catch is four places for one of them to drift.
     */
    private void addShift(JMenu into, String label, String tooltip, Runnable action)
    {
        JMenuItem item = new JMenuItem(I18n.t(label));

        item.addActionListener(event ->
        {
            try
            {
                action.run();
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        });

        item.setToolTipText(I18n.t(tooltip));

        into.add(item);
    }
}
   