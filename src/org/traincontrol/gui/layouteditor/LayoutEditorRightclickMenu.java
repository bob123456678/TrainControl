package org.traincontrol.gui.layouteditor;

import java.awt.Font;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.Route;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * This class represents a right-click menu on the track editor
 * @author Adam
 */
final class LayoutEditorRightclickMenu extends JPopupMenu
{        
    /**
     * The editor this menu belongs to, and the parent of every message it shows: by the time an item runs the menu has
     * left its window, so a message parented on the menu opened beneath an editor that takes Always on Top.
     */
    private final LayoutEditor owner;

    public LayoutEditorRightclickMenu(LayoutEditor edit, TrainControlUI ui, LayoutLabel label, LayoutDiagramComponent component)
    {        
        this.owner = edit;

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
                    edit.executeTool(label);
                }
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog(edit, e.getMessage());
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

            // And the way out, beside the three ways in: Escape does it too, but this menu is where a user who has
            // picked twenty squares by mistake comes looking.
            JMenuItem deselect = new JMenuItem(I18n.t("layout.ui.menuDeselectAll"));
            deselect.addActionListener(event -> edit.clearSelection());
            deselect.setToolTipText("Escape");
            deselect.setEnabled(!edit.getSelection().isEmpty());
            selectionMenu.add(deselect);


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

            // Cut: moving a run of track otherwise meant copying, pasting and deleting the originals, and the last step
            // is the one that gets forgotten and leaves a duplicate railway behind.
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

            // One "Deselect All" only (the same action as the removed "Clear Selection"): it sits with the three ways
            // to select, where a user undoing a selection looks first.
            selectionMenu.setToolTipText(I18n.t("layout.ui.tooltipSelection"));

            add(selectionMenu);

            // At the top level, not inside the submenu above: it is the way INTO picking, so it has to be where
            // somebody trying to make a selection will look.  Dragging cannot pick by default over track (a drag that
            // begins on a tile keeps moving that tile), so this asks for one box and then gets out of the way rather
            // than leaving a mode running.
            JMenuItem oneBox = new JMenuItem(I18n.t("layout.ui.menuSelectByDragging"));
            oneBox.addActionListener(event -> edit.selectOnce());
            oneBox.setToolTipText(I18n.t("layout.ui.tooltipSelectByDragging") + "  ("
                + LayoutEditor.SHORTCUT_SELECT_BY_DRAGGING + ")");
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
                JOptionPane.showMessageDialog(edit, e.getMessage());
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
                JOptionPane.showMessageDialog(edit, e.getMessage());
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
                    JOptionPane.showMessageDialog(edit, e.getMessage());
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
                    JOptionPane.showMessageDialog(edit, e.getMessage());
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
                        JOptionPane.showMessageDialog(edit, e.getMessage());
                    }
                });
                menuItem.setToolTipText("Control+R");

                // add() belongs inside this block: for a text tile or a symmetric one (no Rotate) menuItem still names
                // the Copy item added above, and Container.add() on a component already in the menu silently moves it
                // to the end.
                add(menuItem);
            }

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
                        JOptionPane.showMessageDialog(edit, e.getMessage());
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
                                JOptionPane.showMessageDialog(edit, e.getMessage());
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
                    JOptionPane.showMessageDialog(edit, e.getMessage());
                }
            });
            menuItem.setToolTipText("Control+T");
            add(menuItem);
            
            // Station labels are placed in the autonomy editor, where the stations are decided; this item wrote a
            // "Point:" label from the LEGACY graph's point names.  Its Control+S shortcut is gone with it.

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
                    JOptionPane.showMessageDialog(edit, e.getMessage());
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
        // The four "shift the whole diagram" items below each insert a row or a column at the hovered square and push
        // everything past it along.  Dragging moves the squares you picked; these move everything below or to the right
        // of one square, which is not a selection anybody wants to make by hand.  Each takes a snapshot first, so a
        // mis-aimed one is a Control+Z away.
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
                JOptionPane.showMessageDialog(edit, e.getMessage());
            }
        });

        // growEdges() itself refuses at MAX_SIZE with layout.ui.errorMaxSizeExceeded, so the item must not stay live at
        // the ceiling printing "(60 x 30)": this mirrors the editor's own predicate, as Shift Down and Shift Right do,
        // and swaps the tooltip to the reason.
        boolean canGrow = edit.roomToGrow(1, 1);
        menuItem.setEnabled(canGrow);
        menuItem.setToolTipText(canGrow ? "Control+I"
            : I18n.f("layout.ui.errorMaxSizeExceeded", LayoutEditor.MAX_SIZE));
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
                JOptionPane.showMessageDialog(edit, e.getMessage());
            }
        });

        // Matches shrinkEdges()'s own check, with the sentence it shows when refused as the tooltip, so a grayed item
        // gives its reason as the rest of this menu does.
        boolean canShrink = edit.getMarklinLayout().edgesAreEmpty();
        menuItem.setEnabled(canShrink);
        menuItem.setToolTipText(canShrink ? null : I18n.t("layout.ui.errorEdgesNotEmpty"));
        diagramSubmenu.add(menuItem);

        diagramSubmenu.addSeparator();

        // In the order somebody reaches for them: down and right make room, up and left take it back.
        addShift(diagramSubmenu, "layout.ui.shiftDown", "layout.ui.tooltip.shiftDiagramDown",
            () -> edit.shiftDown(), edit.canShiftDown());

        addShift(diagramSubmenu, "layout.ui.shiftRight", "layout.ui.tooltip.shiftDiagramRight",
            () -> edit.shiftRight(), edit.canShiftRight());

        // Grayed where they would refuse, with the reason - the same treatment Shrink gets above, and
        // asking the editor's own predicate rather than restating it here.
        addShift(diagramSubmenu, "layout.ui.shiftUp", "layout.ui.tooltip.shiftDiagramUp",
            () -> edit.shiftUp(), edit.canShiftUp());

        addShift(diagramSubmenu, "layout.ui.shiftLeft", "layout.ui.tooltip.shiftDiagramLeft",
            () -> edit.shiftLeft(), edit.canShiftLeft());

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
                JOptionPane.showMessageDialog(edit, e.getMessage());
            }
        });
        
        diagramSubmenu.add(menuItem);
        
        add(diagramSubmenu);

        // SHOW COORDINATES IS NOT HERE ANY MORE (OB-179).  Adam: "hide the show coordinates right click menu option...
        // this is tied to the show grid option and will be toggled on and off together with the grid."  See
        // `LayoutGrid.coordinatesVisible`.
    }

    /** One of the four shift items, written once: they differ only in their words and the method they call. */
    private void addShift(JMenu into, String label, String tooltip, Runnable action, boolean enabled)
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
                JOptionPane.showMessageDialog(owner, e.getMessage());
            }
        });

        // Disabled, say why - the pattern this menu follows.  Enabled, the item's own
        // tooltip describes what it does.
        item.setEnabled(enabled);
        item.setToolTipText(enabled ? I18n.t(tooltip) : I18n.t("layout.ui.errorShiftAtEdge"));

        into.add(item);
    }
}
   