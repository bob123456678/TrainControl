package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ItemEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Point;

/**
 * This class represents a right-click menu when the graph UI is clicked on a point
 * @author Adam
 */
final class GraphRightClickPointMenu extends JPopupMenu
{
    JMenuItem menuItem;
    TrainControlUI tcui;

    public GraphRightClickPointMenu(TrainControlUI ui, Point p, GraphViewer parent)
    {       
        String nodeName = p.getName();
        this.tcui = ui;

        if (p.isDestination())
        {
            // Select the active locomotive
            if (!ui.getModel().getAutoLayout().getLocomotivesToRun().isEmpty())
            {
                menuItem = new JMenuItem("Edit Locomotive at " + nodeName);
                menuItem.addActionListener(event -> 
                    {
                        GraphLocAssign edit = new GraphLocAssign(ui, p, false);

                        int dialogResult = JOptionPane.showConfirmDialog((Component) parent.getSwingView(), edit, "Edit / Assign Locomotive", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                        if(dialogResult == JOptionPane.OK_OPTION)
                        {
                            edit.commitChanges();
                        }
                    }
                );    

                add(menuItem);
            }

            menuItem = new JMenuItem("Add new Locomotive to graph at " + nodeName);
            menuItem.addActionListener(event -> 
                {
                    if (ui.getModel().getLocomotives().isEmpty())
                    {
                        JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                            TrainControlUI.NO_LOC_MESSAGE
                        );
                    }
                    else
                    {
                        GraphLocAssign edit = new GraphLocAssign(ui, p, true);

                        if (edit.getNumLocs() == 0)
                        {
                            JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                                "All locomotives in the database have already been placed on the graph."
                            );
                        }
                        else
                        {
                            int dialogResult = JOptionPane.showConfirmDialog((Component) parent.getSwingView(), edit, "Place New Locomotive", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                            if(dialogResult == JOptionPane.OK_OPTION)
                            {
                                edit.commitChanges();
                            }
                        }
                    }
                }
            ); 

            add(menuItem);

            if (!ui.getModel().getAutoLayout().getLocomotivesToRun().isEmpty() && ui.getActiveLoc() != null)
            {
                menuItem = new JMenuItem("Assign " + ui.getActiveLoc().getName() + " to Node");
                menuItem.setToolTipText("Control+V");
                menuItem.addActionListener(event -> 
                    {
                        ui.getModel().getAutoLayout().moveLocomotive(ui.getActiveLoc().getName(), nodeName, false);
                        ui.repaintAutoLocList(false);
                    }
                );    

                add(menuItem);
            }

            addSeparator();

            if (p.isOccupied())
            {
                menuItem = new JMenuItem("Remove Locomotive " + p.getCurrentLocomotive().getName() + " from Node");
                menuItem.addActionListener(event -> { ui.getModel().getAutoLayout().moveLocomotive(null, nodeName, false); ui.repaintAutoLocList(false);});    
                add(menuItem);

                menuItem = new JMenuItem("Remove Locomotive " + p.getCurrentLocomotive().getName() + " from Graph");
                menuItem.setToolTipText("Delete");
                menuItem.addActionListener(event -> { ui.getModel().getAutoLayout().moveLocomotive(null, nodeName, true); ui.repaintAutoLocList(false); });    
                add(menuItem);

                addSeparator();
            }
        }

        // Edit sensor
        menuItem = new JMenuItem("Edit s88 address (" + (p.hasS88() ? p.getS88() : "none") + ")");
        menuItem.setToolTipText("Control+S");
        menuItem.addActionListener(event -> 
            {
                parent.setS88(p);
            }
        );     

        add(menuItem);
        
        // Create a submenu for the remaining items
        JMenu submenu = new JMenu("Edit advanced parameters...");
        
        if (p.isDestination())
        {              
            menuItem = new JMenuItem("Maximum train length (" + (p.getMaxTrainLength() != 0 ? p.getMaxTrainLength() : "any") + ")"); //  at " + nodeName + "
            menuItem.addActionListener(event -> 
                {
                    String dialogResult = JOptionPane.showInputDialog((Component) parent.getSwingView(), 
                        "Enter the maximum length of a train that can stop at this station.",
                        p.getMaxTrainLength());

                    if (dialogResult != null)
                    {
                        try
                        {
                            int newLength = Math.abs(Integer.parseInt(dialogResult));
                            p.setMaxTrainLength(newLength);
                            ui.updatePoint(p, parent.getMainGraph());
                            ui.repaintAutoLocList(false);
                        }
                        catch (NumberFormatException e)
                        {
                            JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                                "Invalid value (must be a positive integer, or 0 to disable)");
                        }
                    }
                }
            );     

            submenu.add(menuItem);
        }

        // Edit Priority
        if (p.isDestination())
        {    
            menuItem = new JMenuItem("Station priority (" + (p.getPriority() != 0 ? (p.getPriority() > 0 ? "+" : "") + p.getPriority() : "default") + ")");
            menuItem.addActionListener(event -> 
                {
                    String dialogResult = JOptionPane.showInputDialog((Component) parent.getSwingView(), 
                        "Enter the priority for " + nodeName + " (negative is lower, positive is higher):",
                        p.getPriority());

                    if (dialogResult != null)
                    {
                        dialogResult = dialogResult.trim();

                        try
                        {
                            Integer value;
                            if (dialogResult.equals(""))
                            {
                                value = null;
                            }
                            else
                            {
                                value = Integer.valueOf(dialogResult);
                            }

                            p.setPriority(value);

                            ui.updatePoint(p, parent.getMainGraph());

                            ui.repaintAutoLocList(false);
                        }
                        catch (NumberFormatException e)
                        {
                            JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                                "Invalid value (must be an integer, or 0 for default)");
                        }
                    }
                }
            );     

            submenu.add(menuItem);
        }

        // Excluded locomotives
        menuItem = new JMenuItem("Excluded locomotives (" + p.getExcludedLocs().size() + ")");
        menuItem.setToolTipText("Control+E/U to exclude/unexclude active locomotive");
        menuItem.addActionListener(event -> 
        {
            try
            {
                GraphLocExclude edit = new GraphLocExclude(ui, p);

                int dialogResult2 = JOptionPane.showConfirmDialog((Component) parent.getSwingView(), edit, 
                        "Edit Excluded Locomotives at " + p.getName(), 
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

                if (dialogResult2 == JOptionPane.OK_OPTION)
                {
                    p.setExcludedLocs(edit.getSelectedExcludeLocs());
                }

                ui.updatePoint(p, parent.getMainGraph());
                ui.repaintAutoLocList(true);
            }
            catch (Exception e)
            {
                JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                    "Error editing point: " + e.getMessage());
            }
        });

        submenu.add(menuItem);

        // Speed multiplier         
        menuItem = new JMenuItem("Speed multiplier (" + p.getSpeedMultiplierPercent() + "%)");
        menuItem.addActionListener(event ->
        {
            try
            {
                // Prefill the dialog with the current value
                Object input = JOptionPane.showInputDialog(
                    (Component) parent.getSwingView(),
                    "Enter speed multiplier (1-200%) for " + nodeName + ":",
                    "Set Speed Multiplier",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    p.getSpeedMultiplierPercent()
                );

                if (input != null && input instanceof String)
                {
                    p.setSpeedMultiplier(Integer.parseInt(input.toString()) * 0.01); // Convert to multiplier
                }
            } 
            catch (NumberFormatException ex)
            {
                JOptionPane.showMessageDialog(
                    (Component) parent.getSwingView(),
                    "Invalid input. Please enter a valid integer.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
            catch (Exception ex)
            {
                JOptionPane.showMessageDialog(
                    (Component) parent.getSwingView(),
                    ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });

        menuItem.setToolTipText("Optionally adjust the speed of trains incoming to this point.");
        submenu.add(menuItem);

        add(submenu);
        
        addSeparator();

        // Allow changes because locomotives on non-stations will by design not run
        //if (!p.isDestination() || !p.isOccupied())        
        JCheckBoxMenuItem stationCheckbox = new JCheckBoxMenuItem("Mark as Station", p.isDestination());
        stationCheckbox.addItemListener(event ->
        {
            try
            {
                if (!p.hasS88()) parent.setS88(p);
                
                p.setDestination(stationCheckbox.isSelected());
                // Refresh UI updates
                ui.updatePoint(p, parent.getMainGraph());
                ui.repaintAutoLocList(false);
            }
            catch (Exception ex)
            {
                JOptionPane.showMessageDialog(parent, ex.getMessage());
            }
        });

        add(stationCheckbox);

        // Create "Terminus Station" checkbox
        JCheckBoxMenuItem terminusCheckbox = new JCheckBoxMenuItem("Mark as Terminus Station", p.isTerminus());
        JCheckBoxMenuItem reversingCheckbox = new JCheckBoxMenuItem("Mark as Reversing Point", p.isReversing());
        
        stationCheckbox.setToolTipText("Trains can stop at stations.");
        terminusCheckbox.setToolTipText("Terminus stations act like stations but will change the direction of the train.");
        reversingCheckbox.setToolTipText("Trains will change directions at reversing points, but will never end at them in autonomous operation.");
        
        terminusCheckbox.addItemListener(event ->
        {
            try
            {
                if (!p.hasS88()) parent.setS88(p);
                
                p.setDestination(true);
                
                if (p.isReversing())
                {
                    p.setReversing(false);
                }

                p.setTerminus(terminusCheckbox.isSelected());
                // Refresh UI updates
                ui.updatePoint(p, parent.getMainGraph());
                ui.repaintAutoLocList(false);
            }
            catch (Exception ex)
            {
                JOptionPane.showMessageDialog(parent, ex.getMessage());
            }
        });

        add(terminusCheckbox);

        // Create "Reversing Point" checkbox
        reversingCheckbox.addItemListener(event ->
        {
            try
            {
                if (p.isTerminus())
                {
                    p.setTerminus(false);
                }
                
                p.setReversing(reversingCheckbox.isSelected());
                // Refresh UI updates
                ui.updatePoint(p, parent.getMainGraph());
                ui.repaintAutoLocList(false);
            }
            catch (Exception ex)
            {
                JOptionPane.showMessageDialog(parent, ex.getMessage());
            }
        });

        add(reversingCheckbox);

        addSeparator();

        // Enable/disable point
        // Create a checkbox menu item for the active state
        JCheckBoxMenuItem activeCheckbox = new JCheckBoxMenuItem("Active", p.isActive());
        activeCheckbox.setToolTipText("Inactive points will not be traversed in autonomous operation.");
        activeCheckbox.addItemListener(event ->
        {
            try
            {
                // Toggle the active state of the point
                p.setActive(activeCheckbox.isSelected());
                // Refresh the UI after the change
                ui.updatePoint(p, parent.getMainGraph());
                ui.repaintAutoLocList(false);
            }
            catch (Exception ex)
            {
                JOptionPane.showMessageDialog(parent, ex.getMessage());
            }
        });

        add(activeCheckbox);  

        // Add edge
        addSeparator();
        
        final String lastClickedNode = parent.getLastClickedNode();

        if (lastClickedNode != null && !lastClickedNode.equals(p.getName()))
        {
            menuItem = new JMenuItem("Connect to " + lastClickedNode);
            menuItem.setToolTipText("Last node that was left-clicked.");
            menuItem.addActionListener(event -> 
            {
                try
                {
                    if (ui.getModel().getAutoLayout().getPoint(lastClickedNode) == null)
                    {
                        JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                            "This point name does not exist.");
                    }
                    else
                    {
                        // Add the edge
                        ui.getModel().getAutoLayout().createEdge(nodeName, lastClickedNode);

                        Edge e = ui.getModel().getAutoLayout().getEdge(nodeName, lastClickedNode);

                        ui.addEdge(e, parent.getMainGraph());
                        ui.repaintAutoLocList(false);
                        ui.getModel().getAutoLayout().refreshUI();
                        parent.setLastClickedNode(null);
                    }
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                        "Error adding edge: " + e.getMessage());
                }
            });

            add(menuItem);
        }

        menuItem = new JMenuItem("Connect to Point...");
        menuItem.addActionListener(event -> 
        {
            // Get all point names except this one
            Collection<Point> points = ui.getModel().getAutoLayout().getPoints();
            List<String> pointNames = new LinkedList<>();

            for (Point p2 : points)
            {
                pointNames.add(p2.getName());
            }

            Collections.sort(pointNames);

            // Remove self and all existing neighbors
            pointNames.remove(nodeName);

            for (Edge e2 : ui.getModel().getAutoLayout().getNeighbors(p))
            {
                pointNames.remove(e2.getEnd().getName());
            }

            if (!pointNames.isEmpty())
            {
                String dialogResult = (String) JOptionPane.showInputDialog((Component) parent.getSwingView(), 
                        "Choose the name of the station/point you want to connect " + nodeName + " to:",
                        "Add New Edge", JOptionPane.QUESTION_MESSAGE, null, 
                        pointNames.toArray(), // Array of choices
                        pointNames.get(0));

                if (dialogResult != null && !"".equals(dialogResult))
                {
                    try
                    {
                        if (ui.getModel().getAutoLayout().getPoint(dialogResult) == null)
                        {
                            JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                                "This point name does not exist.");
                        }
                        else
                        {
                            // Add the edge
                            ui.getModel().getAutoLayout().createEdge(nodeName, dialogResult);

                            Edge e = ui.getModel().getAutoLayout().getEdge(nodeName, dialogResult);

                            ui.addEdge(e, parent.getMainGraph());
                            ui.repaintAutoLocList(false);
                            ui.getModel().getAutoLayout().refreshUI();
                        }
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                            "Error adding edge.");
                    }
                }
            }
            else
            {
                JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                    "No other points to connect to.  Add more points to the graph.");
            }
        }); 

        add(menuItem);

        if (!ui.getModel().getAutoLayout().getNeighborsAndIncoming(p).isEmpty())
        {
            menuItem = new JMenuItem("Edit Edge...");

            menuItem.addActionListener(event -> 
                {
                    // This will return not null if the window is visible
                    if (parent.getGraphEdgeEditor() != null)
                    {
                        JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                            "Only one edge can be edited at a time.");
                        parent.getGraphEdgeEditor().toFront();
                        parent.getGraphEdgeEditor().requestFocus();
                        return;
                    }

                    // Get all point names except this one
                    List<String> edgeNames = new LinkedList<>();

                    for (Edge e2 : ui.getModel().getAutoLayout().getNeighborsAndIncoming(p))
                    {
                        edgeNames.add(e2.getName());
                    }

                    // Collections.sort(edgeNames);

                    String dialogResult;

                    // Only prompt if there are multiple outgoing edges
                    if (edgeNames.size() > 1)
                    {
                        dialogResult = showEdgeSelectionDialog(parent, edgeNames, "Select an edge to edit");
                    }
                    else
                    {
                        dialogResult = edgeNames.get(0);
                    }

                    if (dialogResult != null && !"".equals(dialogResult))
                    {
                        try
                        {
                            if (ui.getModel().getAutoLayout().getEdge(dialogResult) == null)
                            {
                                JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                                    "This edge name does not exist.");
                            }
                            else
                            {
                                parent.setGraphEdgeEdit(new GraphEdgeEdit(ui, parent.getMainGraph(), ui.getModel().getAutoLayout().getEdge(dialogResult)));
                                // Align with main window.  Graph window coordinates are not correct for some reason
                                parent.getGraphEdgeEdit().setLocation(
                                    ui.getX() + (ui.getWidth() - parent.getGraphEdgeEdit().getWidth()) / 2,
                                    ui.getY() + (ui.getHeight() - parent.getGraphEdgeEdit().getHeight()) / 2
                                );
                            }
                        }
                        catch (Exception e)
                        {
                            JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                                "Error editing edge: " + e.getMessage());
                        }
                    }

                }
            ); 

            add(menuItem);
        }

        // Copy edge
        if (!ui.getModel().getAutoLayout().getNeighbors(p).isEmpty())
        {
            menuItem = new JMenuItem("Copy outgoing Edge...");
            menuItem.addActionListener(event -> 
                {
                    // Get all point names except this one
                    List<String> edgeNames = new LinkedList<>();

                    for (Edge e2 : ui.getModel().getAutoLayout().getNeighbors(p))
                    {
                        edgeNames.add(e2.getName());
                    }

                    Collections.sort(edgeNames);

                    String dialogResult;

                    // Only prompt if there are multiple outgoing edges
                    if (edgeNames.size() > 1)
                    {
                        dialogResult = showEdgeSelectionDialog(parent, edgeNames, "Select an edge to copy");
                    }
                    else
                    {
                        dialogResult = edgeNames.get(0);
                    }

                    if (dialogResult != null && !"".equals(dialogResult))
                    {
                        try
                        {
                            if (ui.getModel().getAutoLayout().getEdge(dialogResult) == null)
                            {
                                JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                                    "This edge name does not exist.");
                            }
                            else
                            {
                                Edge original = ui.getModel().getAutoLayout().getEdge(dialogResult);
                                
                                ui.highlightLockedEdges(original, new LinkedList<>());

                                String[] options = {"Start", "End"};
                                int res = JOptionPane.showOptionDialog((Component) parent.getSwingView(), "Change which point?", "Copy Type",
                                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, 
                                    options, options[0]);

                                // Do nothing after pressing escape
                                if (res != JOptionPane.YES_OPTION && res != JOptionPane.NO_OPTION)
                                {
                                    ui.highlightLockedEdges(null, null);
                                    return;
                                }

                                boolean changeEnd = (res == 1);

                                // Get all point names except this one
                                Collection<Point> points = ui.getModel().getAutoLayout().getPoints();
                                List<String> pointNames = new LinkedList<>();

                                for (Point p2 : points)
                                {
                                    pointNames.add(p2.getName());
                                }

                                Collections.sort(pointNames);

                                // Remove self and all existing neighbors
                                pointNames.remove(nodeName);

                                if (changeEnd)
                                {
                                    for (Edge e2 : ui.getModel().getAutoLayout().getNeighbors(p))
                                    {
                                        pointNames.remove(e2.getEnd().getName());
                                    }
                                }
                                else
                                {
                                    // Remove current end
                                    pointNames.remove(original.getEnd().getName());
                                }

                                if (!pointNames.isEmpty())
                                {
                                    dialogResult = (String) JOptionPane.showInputDialog((Component) parent.getSwingView(), 
                                            "Choose the name of the station/point you wish to connect " + (changeEnd ? "to" : "from") + ":",
                                            "Copy Edge", JOptionPane.QUESTION_MESSAGE, null, 
                                            pointNames.toArray(), // Array of choices
                                            pointNames.get(0));

                                    if (dialogResult != null && !"".equals(dialogResult))
                                    {
                                        Edge e = ui.getModel().getAutoLayout().copyEdge(original, dialogResult, changeEnd);

                                        ui.addEdge(e, parent.getMainGraph());
                                        ui.repaintAutoLocList(false);
                                        ui.getModel().getAutoLayout().refreshUI();
                                    }
                                }
                                else
                                {
                                    throw new Exception("There are no other valid points to connect to.");
                                }
                            }
                        }
                        catch (Exception e)
                        {
                            JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                                "Error copying edge: " + e.getMessage());
                        }
                        
                        ui.highlightLockedEdges(null, null);
                    }
                }
            ); 

            add(menuItem);
        }

        // Delete edges
        List<Edge> neighbors =  ui.getModel().getAutoLayout().getNeighborsAndIncoming(p);
        if (!neighbors.isEmpty())
        {                    
            addSeparator();

            menuItem = new JMenuItem("Delete Edge...");
            menuItem.setForeground(Color.RED);
            menuItem.addActionListener(event -> 
                {
                    // Get all point names except this one
                    List<String> edgeNames = new LinkedList<>();

                    for (Edge e2 : ui.getModel().getAutoLayout().getNeighborsAndIncoming(p))
                    {
                        edgeNames.add(e2.getName());
                    }

                    // No longer necessary as getNeighborsAndIncoming sorts by outgoing first
                    // Collections.sort(edgeNames);

                    String dialogResult;

                    // Only prompt if there are multiple outgoing edges
                    if (edgeNames.size() > 1)
                    {
                        dialogResult = showEdgeSelectionDialog(parent, edgeNames, "Which edge do you want to delete?");
                    }
                    else
                    {
                        dialogResult = edgeNames.get(0);
                    }

                    if (dialogResult != null && !"".equals(dialogResult))
                    {                            
                        try
                        {
                            Edge e = ui.getModel().getAutoLayout().getEdge(dialogResult);

                            List<Edge> highlight = new LinkedList<>();
                            highlight.add(e);
                            
                            ui.highlightLockedEdges(null, highlight);

                            int confirmation = JOptionPane.showConfirmDialog((Component) parent.getSwingView(), 
                                "This will permanently remove the edge from " + e.getStart().getName() + " to " + e.getEnd().getName() + " from the graph.  Proceed?", 
                                "Edge Deletion", JOptionPane.YES_NO_OPTION
                            );
                                           
                            if (confirmation == JOptionPane.YES_OPTION)
                            {
                                ui.getModel().getAutoLayout().deleteEdge(e.getStart().getName(), e.getEnd().getName());
                                parent.getMainGraph().removeEdge(e.getUniqueId());
                                ui.getModel().getAutoLayout().refreshUI();
                                ui.repaintAutoLocList(false);
                            }
                        }
                        catch (Exception e)
                        {
                            JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                                "Error deleting edge: " + e.getMessage());
                        }
                        
                        ui.highlightLockedEdges(null, null);
                    }   
                }
            ); 

            add(menuItem);
        }
        
        // Rename option applicable to all nodes
        addSeparator();

        menuItem = new JMenuItem("Rename " + nodeName);
        menuItem.addActionListener(event -> 
        {
            String dialogResult = JOptionPane.showInputDialog((Component) parent.getSwingView(), 
                "Enter the new point name.",
                nodeName);

            if (dialogResult != null && !"".equals(dialogResult))
            {
                try
                {
                    if (ui.getModel().getAutoLayout().getPoint(dialogResult) != null)
                    {
                        JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                            "This station name is already in use.  Pick another.");
                    }
                    else
                    {
                        ui.getModel().getAutoLayout().renamePoint(nodeName, dialogResult);
                        ui.repaintAutoLocList(false);
                        ui.updatePoint(p, parent.getMainGraph());
                    }
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog((Component) parent.getSwingView(),
                        "Error renaming node.");
                }
            }
        });  

        add(menuItem);

        // Delete point
        addSeparator();

        menuItem = new JMenuItem("Delete Point");
        menuItem.setForeground(Color.RED);
        menuItem.addActionListener(event -> 
        {
            int dialogResult = JOptionPane.showConfirmDialog((Component) parent.getSwingView(), 
                    "This will entirely remove " + nodeName + " from the graph.  Proceed?", 
                    "Point Deletion", JOptionPane.YES_NO_OPTION);
            if(dialogResult == JOptionPane.YES_OPTION)
            {
                try 
                {
                    parent.setLastClickedNode(null);
                    parent.setLastHoveredNode(null);
                    ui.getModel().getAutoLayout().deletePoint(p.getName());
                    parent.getMainGraph().removeNode(p.getUniqueId());
                    ui.repaintAutoLocList(false);
                } 
                catch (Exception e)
                {
                   JOptionPane.showMessageDialog((Component) parent.getSwingView(), e.getMessage());
                }
            } 
        });    

        add(menuItem);    
    }
    
    private String showEdgeSelectionDialog(Component parent, java.util.List<String> edgeNames, String message)
    {
        JComboBox<String> edgeDropdown = new JComboBox<>(edgeNames.toArray(new String[0]));

        tcui.highlightLockedEdges(tcui.getModel().getAutoLayout().getEdge(edgeNames.get(0)), new LinkedList<>());
        
        // Add an ItemListener to detect when the user selects a different option
        edgeDropdown.addItemListener((ItemEvent e) ->
        {
            if (e.getStateChange() == ItemEvent.SELECTED)
            {
                String selectedEdge = (String) e.getItem();
                
                handleEdgeSelection(selectedEdge);
            }
        });

        // Show the JComboBox in a JOptionPane
        int result = JOptionPane.showConfirmDialog(parent, 
            edgeDropdown, 
            message, 
            JOptionPane.OK_CANCEL_OPTION, 
            JOptionPane.QUESTION_MESSAGE);
        
        tcui.highlightLockedEdges(null, null);

        // If the user clicks "OK," retrieve the selected item
        if (result == JOptionPane.OK_OPTION)
        {
            return (String) edgeDropdown.getSelectedItem();
        }
        // Cancel or close
        else
        {
            return null;
        }
    }

    // Custom method to handle the selected edge
    private void handleEdgeSelection(String edgeName)
    {
        tcui.highlightLockedEdges(tcui.getModel().getAutoLayout().getEdge(edgeName), new LinkedList<>());        
    }
}
   