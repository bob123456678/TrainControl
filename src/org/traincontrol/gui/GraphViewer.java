package org.traincontrol.gui;

import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.algorithm.Toolkit;
import org.graphstream.ui.geom.Point2;
import org.graphstream.ui.geom.Point3;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.graphicGraph.GraphicGraph;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.swing_viewer.util.DefaultMouseManager;
import org.graphstream.ui.swing_viewer.util.DefaultShortcutManager;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.View;
import org.graphstream.ui.view.camera.Camera;
import org.graphstream.ui.view.util.InteractiveElement;

/**
 * Autonomy graph UI
 */
final public class GraphViewer extends PositionAwareJFrame
{    
    private TrainControlUI parent;
    private SwingViewer swingViewer;
    private final View swingView;
    private final Graph mainGraph;
    
    private GraphEdgeEdit graphEdgeEdit;
    
    private String lastHoveredNode;
    private String lastClickedNode;
    
    private Locomotive clipboard;
    
    public static final String WINDOW_TITLE = "Autonomy Graph";
    
    public Graph getMainGraph()
    {
        return mainGraph;
    }
    
    public GraphEdgeEdit getGraphEdgeEditor()
    {
        if (this.graphEdgeEdit != null && this.graphEdgeEdit.isVisible())
        {
            return graphEdgeEdit;
        }
        
        return null;
    }
    
    /**
     * Opens a dialog to set the S88 sensor of a given point
     * @param p 
     */
    private void setS88 (Point p)
    {
        if (p != null)
        {
            String dialogResult = JOptionPane.showInputDialog((Component) swingView, 
                "Enter the s88 sensor address for " + p.getName() + ":",
                p.getS88());

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

                    p.setS88(value);

                    parent.updatePoint(p, mainGraph);

                    parent.repaintAutoLocList(false);
                }
                catch (NumberFormatException e)
                {
                    JOptionPane.showMessageDialog((Component) swingView,
                        "Invalid value (must be a non-negative integer, or blank to disable if not a station)");
                }
            }
        }
    }
    
    final class RightClickMenu extends JPopupMenu
    {
        JMenuItem menuItem;

        public RightClickMenu(TrainControlUI ui, Point p)
        {       
            String nodeName = p.getName();
            
            if (p.isDestination())
            {
                // Select the active locomotive
                if (!parent.getModel().getAutoLayout().getLocomotivesToRun().isEmpty())
                {
                    menuItem = new JMenuItem("Edit Locomotive at " + nodeName);
                    menuItem.addActionListener(event -> 
                        {
                            GraphLocAssign edit = new GraphLocAssign(parent, p, false);

                            int dialogResult = JOptionPane.showConfirmDialog((Component) swingView, edit, "Edit / Assign Locomotive", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
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
                        if (parent.getModel().getLocomotives().isEmpty())
                        {
                            JOptionPane.showMessageDialog((Component) swingView,
                                TrainControlUI.NO_LOC_MESSAGE
                            );
                        }
                        else
                        {
                            GraphLocAssign edit = new GraphLocAssign(parent, p, true);
                            
                            if (edit.getNumLocs() == 0)
                            {
                                JOptionPane.showMessageDialog((Component) swingView,
                                    "All locomotives in the database have already been placed on the graph."
                                );
                            }
                            else
                            {
                                int dialogResult = JOptionPane.showConfirmDialog((Component) swingView, edit, "Place New Locomotive", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                                if(dialogResult == JOptionPane.OK_OPTION)
                                {
                                    edit.commitChanges();
                                }
                            }
                        }
                    }
                ); 

                add(menuItem);
                
                if (!parent.getModel().getAutoLayout().getLocomotivesToRun().isEmpty() && parent.getActiveLoc() != null)
                {
                    menuItem = new JMenuItem("Assign " + parent.getActiveLoc().getName() + " to Node");
                    menuItem.setToolTipText("Control+V");
                    menuItem.addActionListener(event -> 
                        {
                            parent.getModel().getAutoLayout().moveLocomotive(parent.getActiveLoc().getName(), nodeName, false);

                        }
                    );    

                    add(menuItem);
                }
            
                addSeparator();
                
                if (p.isOccupied())
                {
                    menuItem = new JMenuItem("Remove Locomotive " + p.getCurrentLocomotive().getName() + " from Node");
                    menuItem.addActionListener(event -> { parent.getModel().getAutoLayout().moveLocomotive(null, nodeName, false); parent.repaintAutoLocList(false);});    
                    add(menuItem);
                    
                    menuItem = new JMenuItem("Remove Locomotive " + p.getCurrentLocomotive().getName() + " from Graph");
                    menuItem.setToolTipText("Delete");
                    menuItem.addActionListener(event -> { parent.getModel().getAutoLayout().moveLocomotive(null, nodeName, true); parent.repaintAutoLocList(false); });    
                    add(menuItem);
                    
                    addSeparator();
                }
            }
             
            if (p.isDestination())
            {              
                menuItem = new JMenuItem("Edit maximum train length at " + nodeName + " (" + (p.getMaxTrainLength() != 0 ? p.getMaxTrainLength() : "any") + ")");
                menuItem.addActionListener(event -> 
                    {
                        String dialogResult = JOptionPane.showInputDialog((Component) swingView, 
                            "Enter the maximum length of a train that can stop at this station.",
                            p.getMaxTrainLength());
                        
                        if (dialogResult != null)
                        {
                            try
                            {
                                int newLength = Math.abs(Integer.parseInt(dialogResult));
                                p.setMaxTrainLength(newLength);
                                ui.updatePoint(p, mainGraph);
                                parent.repaintAutoLocList(false);
                            }
                            catch (NumberFormatException e)
                            {
                                JOptionPane.showMessageDialog((Component) swingView,
                                    "Invalid value (must be a positive integer, or 0 to disable)");
                            }
                        }
                    }
                );     
            
                add(menuItem);
            }
                
            // Edit Priority
            if (p.isDestination())
            {    
                menuItem = new JMenuItem("Edit station priority (" + (p.getPriority() != 0 ? (p.getPriority() > 0 ? "+" : "") + p.getPriority() : "default") + ")");
                menuItem.addActionListener(event -> 
                    {
                        String dialogResult = JOptionPane.showInputDialog((Component) swingView, 
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

                                ui.updatePoint(p, mainGraph);

                                parent.repaintAutoLocList(false);
                            }
                            catch (NumberFormatException e)
                            {
                                JOptionPane.showMessageDialog((Component) swingView,
                                    "Invalid value (must be an integer, or 0 for default)");
                            }
                        }
                    }
                );     

                add(menuItem);
            }
            
            // Edit sensor
            menuItem = new JMenuItem("Edit s88 address (" + (p.hasS88() ? p.getS88() : "none") + ")");
            menuItem.setToolTipText("Control+S");
            menuItem.addActionListener(event -> 
                {
                    setS88(p);
                }
            );     

            add(menuItem);
            
            // Excluded locomotives
            menuItem = new JMenuItem("Edit excluded locomotives (" + p.getExcludedLocs().size() + ")");
            menuItem.setToolTipText("Control+E/U to exclude/unexclude active locomotive");
            menuItem.addActionListener(event -> 
            {
                try
                {
                    GraphLocExclude edit = new GraphLocExclude(parent, p);

                    int dialogResult2 = JOptionPane.showConfirmDialog((Component) swingView, edit, 
                            "Edit Excluded Locomotives at " + p.getName(), 
                            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

                    if (dialogResult2 == JOptionPane.OK_OPTION)
                    {
                        p.setExcludedLocs(edit.getSelectedExcludeLocs());
                    }

                    ui.updatePoint(p, mainGraph);
                    parent.repaintAutoLocList(true);
                }
                catch (Exception e)
                {
                    JOptionPane.showMessageDialog((Component) swingView,
                        "Error editing point: " + e.getMessage());
                }
            });

            add(menuItem);
            
            // Speed multiplier         
            menuItem = new JMenuItem("Set speed multiplier (" + p.getSpeedMultiplierPercent() + "%)");
            menuItem.addActionListener(event ->
            {
                try
                {
                    // Prefill the dialog with the current value
                    Object input = JOptionPane.showInputDialog(
                        (Component) swingView,
                        "Enter speed multiplier (1-200%):",
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
                        (Component) swingView,
                        "Invalid input. Please enter a valid integer.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
                catch (Exception ex)
                {
                    JOptionPane.showMessageDialog(
                        (Component) swingView,
                        ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            });

            menuItem.setToolTipText("Optionally adjust the speed of trains incoming to this point.");
            add(menuItem);
            
            addSeparator();
                        
            // Allow changes because locomotive on non-stations will by design not run
            //if (!p.isDestination() || !p.isOccupied())
            menuItem = new JMenuItem("Mark as " + (p.isDestination() ? "Non-station" : "Station"));
            menuItem.addActionListener(event -> { 
                try
                { 
                    p.setDestination(!p.isDestination());
                    // parent.getModel().getAutoLayout().refreshUI();
                    ui.updatePoint(p, mainGraph);
                    parent.repaintAutoLocList(false); 
                } 
                catch (Exception ex) 
                { 
                    JOptionPane.showMessageDialog((Component) swingView,
                        ex.getMessage()); 
                }
            });

            add(menuItem);

            menuItem = new JMenuItem("Mark as " + (p.isTerminus() ? "Non-terminus" : "Terminus") + " station");
            menuItem.addActionListener(event -> { 
                try
                { 
                    if (!p.isTerminus()) p.setDestination(true);
                    
                    p.setTerminus(!p.isTerminus());
                    // parent.getModel().getAutoLayout().refreshUI();
                    ui.updatePoint(p, mainGraph);
                    parent.repaintAutoLocList(false); 
                } 
                catch (Exception ex)
                {
                    JOptionPane.showMessageDialog((Component) swingView,
                        ex.getMessage()); 
                }
            });

            add(menuItem);
            
            menuItem = new JMenuItem("Mark as " + (p.isReversing() ? "Non-reversing" : "Reversing") + " point");
            menuItem.addActionListener(event -> { 
                try
                { 
                    p.setReversing(!p.isReversing());
                    // parent.getModel().getAutoLayout().refreshUI();
                    ui.updatePoint(p, mainGraph);
                    parent.repaintAutoLocList(false); 
                } 
                catch (Exception ex) 
                {
                    JOptionPane.showMessageDialog((Component) swingView,
                        ex.getMessage()); 
                }
            });

            add(menuItem);
               
            addSeparator();
            
            // Enable/disable point
            menuItem = new JMenuItem("Mark as " + (p.isActive() ? "Inactive" : "Active"));
            menuItem.addActionListener(event -> { 
                try
                { 
                    p.setActive(!p.isActive());
                    // parent.getModel().getAutoLayout().refreshUI();
                    ui.updatePoint(p, mainGraph);
                    parent.repaintAutoLocList(false); 
                } 
                catch (Exception ex) 
                {
                    JOptionPane.showMessageDialog((Component) swingView,
                        ex.getMessage()); 
                }
            });

            add(menuItem);  
            addSeparator();

            // Rename option applicable to all nodes
            menuItem = new JMenuItem("Rename " + nodeName);
            menuItem.addActionListener(event -> 
            {
                String dialogResult = JOptionPane.showInputDialog((Component) swingView, 
                    "Enter the new station name.",
                    nodeName);

                if (dialogResult != null && !"".equals(dialogResult))
                {
                    try
                    {
                        if (parent.getModel().getAutoLayout().getPoint(dialogResult) != null)
                        {
                            JOptionPane.showMessageDialog((Component) swingView,
                                "This station name is already in use.  Pick another.");
                        }
                        else
                        {
                            parent.getModel().getAutoLayout().renamePoint(nodeName, dialogResult);
                            parent.repaintAutoLocList(false);
                            ui.updatePoint(p, mainGraph);
                        }
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog((Component) swingView,
                            "Error renaming node.");
                    }
                }
            });  
                
            add(menuItem);
                        
            // Add edge
            addSeparator();
            
            if (lastClickedNode != null && !lastClickedNode.equals(p.getName()))
            {
                menuItem = new JMenuItem("Connect to " + lastClickedNode);
                menuItem.setToolTipText("Last node that was left-clicked.");
                menuItem.addActionListener(event -> 
                {
                    try
                    {
                        if (parent.getModel().getAutoLayout().getPoint(lastClickedNode) == null)
                        {
                            JOptionPane.showMessageDialog((Component) swingView,
                                "This point name does not exist.");
                        }
                        else
                        {
                            // Add the edge
                            parent.getModel().getAutoLayout().createEdge(nodeName, lastClickedNode);

                            Edge e = parent.getModel().getAutoLayout().getEdge(nodeName, lastClickedNode);

                            ui.addEdge(e, mainGraph);
                            parent.repaintAutoLocList(false);
                            parent.getModel().getAutoLayout().refreshUI();
                            lastClickedNode = null;
                        }
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog((Component) swingView,
                            "Error adding edge: " + e.getMessage());
                    }
                });
  
                add(menuItem);
            }
            
            menuItem = new JMenuItem("Connect to Point...");
            menuItem.addActionListener(event -> 
            {
                // Get all point names except this one
                Collection<Point> points = parent.getModel().getAutoLayout().getPoints();
                List<String> pointNames = new LinkedList<>();

                for (Point p2 : points)
                {
                    pointNames.add(p2.getName());
                }

                Collections.sort(pointNames);

                // Remove self and all existing neighbors
                pointNames.remove(nodeName);

                for (Edge e2 : parent.getModel().getAutoLayout().getNeighbors(p))
                {
                    pointNames.remove(e2.getEnd().getName());
                }

                if (!pointNames.isEmpty())
                {
                    String dialogResult = (String) JOptionPane.showInputDialog((Component) swingView, 
                            "Choose the name of the station/point you want to connect " + nodeName + " to:",
                            "Add New Edge", JOptionPane.QUESTION_MESSAGE, null, 
                            pointNames.toArray(), // Array of choices
                            pointNames.get(0));

                    if (dialogResult != null && !"".equals(dialogResult))
                    {
                        try
                        {
                            if (parent.getModel().getAutoLayout().getPoint(dialogResult) == null)
                            {
                                JOptionPane.showMessageDialog((Component) swingView,
                                    "This point name does not exist.");
                            }
                            else
                            {
                                // Add the edge
                                parent.getModel().getAutoLayout().createEdge(nodeName, dialogResult);

                                Edge e = parent.getModel().getAutoLayout().getEdge(nodeName, dialogResult);

                                ui.addEdge(e, mainGraph);
                                parent.repaintAutoLocList(false);
                                parent.getModel().getAutoLayout().refreshUI();
                            }
                        }
                        catch (Exception e)
                        {
                            JOptionPane.showMessageDialog((Component) swingView,
                                "Error adding edge.");
                        }
                    }
                }
                else
                {
                    JOptionPane.showMessageDialog((Component) swingView,
                        "No other points to connect to.  Add more points to the graph.");
                }
            }); 
            
            add(menuItem);
            
            if (!parent.getModel().getAutoLayout().getNeighbors(p).isEmpty())
            {
                menuItem = new JMenuItem("Edit outgoing Edge...");
                
                menuItem.addActionListener(event -> 
                    {
                        // This will return not null if the window is visible
                        if (getGraphEdgeEditor() != null)
                        {
                            JOptionPane.showMessageDialog((Component) swingView,
                                "Only one edge can be edited at a time.");
                            getGraphEdgeEditor().toFront();
                            getGraphEdgeEditor().requestFocus();
                            return;
                        }
                        
                        // Get all point names except this one
                        List<String> edgeNames = new LinkedList<>();

                        for (Edge e2 : parent.getModel().getAutoLayout().getNeighbors(p))
                        {
                            edgeNames.add(e2.getName());
                        }

                        Collections.sort(edgeNames);

                        String dialogResult;
                        
                        // Only prompt if there are multiple outgoing edges
                        if (edgeNames.size() > 1)
                        {
                            dialogResult = (String) JOptionPane.showInputDialog((Component) swingView, 
                                "Which edge do you want to edit?",
                                "Edit Edge", JOptionPane.QUESTION_MESSAGE, null, 
                                edgeNames.toArray(), // Array of choices
                                edgeNames.get(0));
                        }
                        else
                        {
                            dialogResult = edgeNames.get(0);
                        }

                        if (dialogResult != null && !"".equals(dialogResult))
                        {
                            try
                            {
                                if (parent.getModel().getAutoLayout().getEdge(dialogResult) == null)
                                {
                                    JOptionPane.showMessageDialog((Component) swingView,
                                        "This edge name does not exist.");
                                }
                                else
                                {
                                    graphEdgeEdit = new GraphEdgeEdit(parent, mainGraph, parent.getModel().getAutoLayout().getEdge(dialogResult));
                                    // Align with main window.  Graph window coordinates are not correct for some reason
                                    graphEdgeEdit.setLocation(
                                        parent.getX() + (parent.getWidth() - graphEdgeEdit.getWidth()) / 2,
                                        parent.getY() + (parent.getHeight() - graphEdgeEdit.getHeight()) / 2
                                    );
                                }
                            }
                            catch (Exception e)
                            {
                                JOptionPane.showMessageDialog((Component) swingView,
                                    "Error editing edge: " + e.getMessage());
                            }
                        }
                        
                    }
                ); 

                add(menuItem);
            }
            
            // Copy edge
            if (!parent.getModel().getAutoLayout().getNeighbors(p).isEmpty())
            {
                menuItem = new JMenuItem("Copy outgoing Edge...");
                menuItem.addActionListener(event -> 
                    {
                        // Get all point names except this one
                        List<String> edgeNames = new LinkedList<>();

                        for (Edge e2 : parent.getModel().getAutoLayout().getNeighbors(p))
                        {
                            edgeNames.add(e2.getName());
                        }

                        Collections.sort(edgeNames);

                        String dialogResult;
                        
                        // Only prompt if there are multiple outgoing edges
                        if (edgeNames.size() > 1)
                        {
                            dialogResult = (String) JOptionPane.showInputDialog((Component) swingView, 
                                "Which edge do you want to copy?",
                                "Copy Edge", JOptionPane.QUESTION_MESSAGE, null, 
                                edgeNames.toArray(), // Array of choices
                                edgeNames.get(0));
                        }
                        else
                        {
                            dialogResult = edgeNames.get(0);
                        }

                        if (dialogResult != null && !"".equals(dialogResult))
                        {
                            try
                            {
                                if (parent.getModel().getAutoLayout().getEdge(dialogResult) == null)
                                {
                                    JOptionPane.showMessageDialog((Component) swingView,
                                        "This edge name does not exist.");
                                }
                                else
                                {
                                    Edge original = parent.getModel().getAutoLayout().getEdge(dialogResult);
                                    
                                    String[] options = { "Start", "End" };
                                    int res = JOptionPane.showOptionDialog((Component) swingView, "Change which point?", "Copy Type",
                                        JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, 
                                        options, options[0]);
                                    
                                    // Do nothing after pressing escape
                                    if (res != JOptionPane.YES_OPTION && res != JOptionPane.NO_OPTION) return;
                                        
                                    boolean changeEnd = (res == 1);
                                
                                    // Get all point names except this one
                                    Collection<Point> points = parent.getModel().getAutoLayout().getPoints();
                                    List<String> pointNames = new LinkedList<>();

                                    for (Point p2 : points)
                                    {
                                        pointNames.add(p2.getName());
                                    }

                                    Collections.sort(pointNames);

                                    // Remove self and all existing neighbors
                                    pointNames.remove(nodeName);

                                    if(changeEnd)
                                    {
                                        for (Edge e2 : parent.getModel().getAutoLayout().getNeighbors(p))
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
                                        dialogResult = (String) JOptionPane.showInputDialog((Component) swingView, 
                                                "Choose the name of the station/point you wish to connect " + (changeEnd ? "to" : "from") + ":",
                                                "Copy Edge", JOptionPane.QUESTION_MESSAGE, null, 
                                                pointNames.toArray(), // Array of choices
                                                pointNames.get(0));

                                        if (dialogResult != null && !"".equals(dialogResult))
                                        {
                                            Edge e = parent.getModel().getAutoLayout().copyEdge(original, dialogResult, changeEnd);

                                            ui.addEdge(e, mainGraph);
                                            parent.repaintAutoLocList(false);
                                            parent.getModel().getAutoLayout().refreshUI();
                                        }
                                    }
                                }
                            }
                            catch (Exception e)
                            {
                                JOptionPane.showMessageDialog((Component) swingView,
                                    "Error copying edge: " + e.getMessage());
                            }
                        }
                    }
                ); 

                add(menuItem);
            }
                        
            // Delete edges
            List<Edge> neighbors =  parent.getModel().getAutoLayout().getNeighbors(p);
            if (!neighbors.isEmpty())
            {                    
                addSeparator();

                menuItem = new JMenuItem("Delete outgoing Edge...");
                menuItem.addActionListener(event -> 
                    {
                        // Get all point names except this one
                        List<String> edgeNames = new LinkedList<>();

                        for (Edge e2 : parent.getModel().getAutoLayout().getNeighbors(p))
                        {
                            edgeNames.add(e2.getName());
                        }

                        Collections.sort(edgeNames);

                        String dialogResult;
                        
                        // Only prompt if there are multiple outgoing edges
                        if (edgeNames.size() > 1)
                        {
                            dialogResult = (String) JOptionPane.showInputDialog((Component) swingView, 
                                "Which edge do you want to permanently delete?",
                                "Delete Edge", JOptionPane.QUESTION_MESSAGE, null, 
                                edgeNames.toArray(), // Array of choices
                                edgeNames.get(0));
                        }
                        else
                        {
                            dialogResult = edgeNames.get(0);
                        }

                        if (dialogResult != null && !"".equals(dialogResult))
                        {                            
                            try
                            {
                                Edge e = parent.getModel().getAutoLayout().getEdge(dialogResult);
                                
                                int confirmation = JOptionPane.showConfirmDialog((Component) swingView, 
                                    "This will entirely remove edge from " + e.getStart().getName() + " to " + e.getEnd().getName() + " from the graph.  Proceed?", 
                                    "Edge Deletion", JOptionPane.YES_NO_OPTION
                                );
                        
                                if (confirmation == JOptionPane.YES_OPTION)
                                {
                                    parent.getModel().getAutoLayout().deleteEdge(e.getStart().getName(), e.getEnd().getName());
                                    mainGraph.removeEdge(e.getUniqueId());
                                    parent.getModel().getAutoLayout().refreshUI();
                                    parent.repaintAutoLocList(false);
                                }
                            }
                            catch (Exception e)
                            {
                                JOptionPane.showMessageDialog((Component) swingView,
                                    "Error deleting edge: " + e.getMessage());
                            }
                        }   
                    }
                ); 

                add(menuItem);
            }
            
            // Delete point
            addSeparator();

            menuItem = new JMenuItem("Delete Point");
            menuItem.addActionListener(event -> 
            {
                int dialogResult = JOptionPane.showConfirmDialog((Component) swingView, 
                        "This will entirely remove " + nodeName + " from the graph.  Proceed?", 
                        "Point Deletion", JOptionPane.YES_NO_OPTION);
                if(dialogResult == JOptionPane.YES_OPTION)
                {
                    try 
                    {
                        lastClickedNode = null;
                        lastHoveredNode = null;
                        parent.getModel().getAutoLayout().deletePoint(p.getName());
                        mainGraph.removeNode(p.getUniqueId());
                        parent.repaintAutoLocList(false);
                    } 
                    catch (Exception e)
                    {
                       JOptionPane.showMessageDialog((Component) swingView, e.getMessage());
                    }
                } 
            });    
            
            add(menuItem);    
        }
    }
    
    final class RightClickMenuNew extends JPopupMenu
    {
        JMenuItem menuItem;

        public RightClickMenuNew(TrainControlUI ui, int x, int y, boolean running)
        {
            if (!running)
            {
                menuItem = new JMenuItem("Create New Point");
                menuItem.addActionListener(event -> 
                {
                    String dialogResult = JOptionPane.showInputDialog((Component) swingView, 
                        "Enter the new point name.",
                        "");

                    if (dialogResult != null && !"".equals(dialogResult))
                    {
                        try
                        {
                            if (parent.getModel().getAutoLayout().getPoint(dialogResult) != null)
                            {
                                JOptionPane.showMessageDialog((Component) swingView,
                                    "This point name is already in use.  Pick another.");
                            }
                            else
                            {
                                parent.getModel().getAutoLayout().createPoint(dialogResult, false, null);

                                Point p = parent.getModel().getAutoLayout().getPoint(dialogResult);

                                p.setX(x);
                                p.setY(y);

                                mainGraph.addNode(p.getUniqueId());
                                mainGraph.getNode(p.getUniqueId()).setAttribute("x", p.getX());
                                mainGraph.getNode(p.getUniqueId()).setAttribute("y", p.getY());
                                mainGraph.getNode(p.getUniqueId()).setAttribute("weight", 3);

                                ui.updatePoint(p, mainGraph);                            
                                parent.getModel().getAutoLayout().refreshUI();
                                parent.repaintAutoLocList(false);
                            }
                        }
                        catch (Exception e)
                        {
                            JOptionPane.showMessageDialog((Component) swingView,
                                "Error adding node.");
                        }
                    }
                }); 

                add(menuItem);

                addSeparator();

                menuItem = new JMenuItem("Start Autonomous Operation");
                menuItem.addActionListener(event -> 
                {
                    try
                    {
                        parent.requestStartAutonomy();
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(this, e.getMessage());
                    }
                });
                
                add(menuItem);

                addSeparator();

                menuItem = new JMenuItem("Clear Locomotives from Graph");
                menuItem.addActionListener(event -> 
                {
                    if (!parent.getModel().getAutoLayout().isRunning())
                    {
                        try
                        {
                            int dialogResult = JOptionPane.showConfirmDialog(
                                (Component) swingView, "This will remove all locomotives from the graph \nexcept for those parked at reversing stations. Are you sure?" , "Confirm Deletion", JOptionPane.YES_NO_OPTION);

                            if(dialogResult == JOptionPane.YES_OPTION)
                            {
                                List<Locomotive> locs = new ArrayList<>(parent.getModel().getAutoLayout().getLocomotivesToRun());

                                for (Locomotive l: locs)
                                {
                                    Point p = parent.getModel().getAutoLayout().getLocomotiveLocation(l);

                                    if (p != null && !p.isReversing() && p.isDestination())
                                    {
                                        parent.getModel().getAutoLayout().moveLocomotive(null, p.getName(), false);
                                        ui.updatePoint(p, mainGraph);
                                    }
                                }

                                parent.repaintAutoLocList(false);
                            }
                        }
                        catch (Exception e)
                        {
                            JOptionPane.showMessageDialog(this, e.getMessage());
                        }
                    }
                });

                add(menuItem);
            }
            else
            {
                menuItem = new JMenuItem("Gracefully Stop Autonomy");
                menuItem.addActionListener(event -> 
                {
                    try
                    {
                        parent.requestStopAutonomy();
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(this, e.getMessage());
                    }
                });    
                
                add(menuItem);
            }
        }
    }
    
    /**
     * Creates new form GraphViewer
     * @param graph
     * @param ui
     * @param autoLayout
     */
    public GraphViewer(Graph graph, TrainControlUI ui, boolean autoLayout)
    {        
        parent = ui;
                
        // Initialize viewer   
        swingViewer = new SwingViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
        swingView = swingViewer.addDefaultView(false);
        mainGraph = graph;
        
        swingViewer.getDefaultView().enableMouseOptions();

        if (autoLayout)
        {
            swingViewer.enableAutoLayout();    
        }
        else
        {
            swingViewer.disableAutoLayout();
        }
        
        // Enable zooming with mouse wheel
        // https://stackoverflow.com/questions/44675827/how-to-zoom-into-a-graphstream-view
        // swingView.getCamera().setViewPercent(1);
        ((Component) swingView).addMouseWheelListener((MouseWheelEvent e) -> {
            e.consume();
            int i = e.getWheelRotation();
            double factor = Math.pow(1.25, i);
            Camera cam = swingView.getCamera();
            double zoom = cam.getViewPercent() * factor;
            Point2 pxCenter  = cam.transformGuToPx(cam.getViewCenter().x, cam.getViewCenter().y, 0);
            Point3 guClicked = cam.transformPxToGu(e.getX(), e.getY());
            double newRatioPx2Gu = cam.getMetrics().ratioPx2Gu/factor;
            double x1 = guClicked.x + (pxCenter.x - e.getX())/newRatioPx2Gu;
            double y1 = guClicked.y - (pxCenter.y - e.getY())/newRatioPx2Gu;
            cam.setViewCenter(x1, y1, 0);
            cam.setViewPercent(zoom);
        });
                
        // Improve quality
        graph.setAttribute("ui.antialias");
        graph.setAttribute("ui.quality");
        
        final GraphViewer g = this;
        
        // Disable the auto layout if a node gets dragged
        swingView.setMouseManager(new DefaultMouseManager()
        {
            GraphicElement lastNode;
            MouseEvent last;
            
            /**
             * Support dragging to move around the graph
             * https://github.com/graphstream/gs-core/issues/239
             * @param evt 
             */
            @Override
            public void mouseDragged(MouseEvent evt)
            {
                GraphicElement element = view.findGraphicElementAt(EnumSet.of(InteractiveElement.NODE), evt.getX(), evt.getY());
               
                if (element != null)
                {
                    super.mouseDragged(evt);
                }
                else
                {
                    if (last != null)
                    {
                        Point3 p1 = swingView.getCamera().getViewCenter();
                        Point3 p2 = swingView.getCamera().transformGuToPx(p1.x, p1.y, 0);
                        int xdelta = evt.getX() - last.getX();
                        int ydelta = evt.getY() - last.getY();
                        p2.x -= xdelta;
                        p2.y -= ydelta;
                        Point3 p3 = swingView.getCamera().transformPxToGu(p2.x, p2.y);
                        swingView.getCamera().setViewCenter(p3.x, p3.y, 0);
                    }
                    
                    last = evt;
                }
            }

            @Override
            public void mouseReleased(MouseEvent evt)
            {
                if (autoLayout)
                {
                    swingViewer.disableAutoLayout();
                }
                
                if (SwingUtilities.isLeftMouseButton(evt))
                {
                    GraphicElement element = view.findGraphicElementAt(EnumSet.of(InteractiveElement.NODE), evt.getX(), evt.getY());
                    
                    // The above sometimes fails if the element has the lowest Y value.  Use stored element instead.
                    if (element == null)
                    {
                        element = lastNode;
                    }
                    
                    if (element != null && last == null)
                    {
                        Node node = swingViewer.getGraphicGraph().getNode(element.getId());
                        
                        // Point3 position = view.getCamera().transformGuToPx(Toolkit.nodePosition(node)[0], Toolkit.nodePosition(node)[1], 0);
                        if (node != null)
                        {
                            parent.getModel().getAutoLayout().getPointById(node.getId()).setX(Double.valueOf(Toolkit.nodePosition(node)[0]).intValue());
                            parent.getModel().getAutoLayout().getPointById(node.getId()).setY(Double.valueOf(Toolkit.nodePosition(node)[1]).intValue());

                            parent.getModel().log("Moved " + parent.getModel().getAutoLayout().getPointById(node.getId()).getName() + " to " + Double.valueOf(Toolkit.nodePosition(node)[0]).intValue() + "," + (Double.valueOf(Toolkit.nodePosition(node)[1]).intValue()));
                        
                            lastClickedNode = parent.getModel().getAutoLayout().getPointById(node.getId()).getName();
                        }
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent evt)
            {
                // Save the element due to the glitch above
                GraphicElement element = view.findGraphicElementAt(EnumSet.of(InteractiveElement.NODE), evt.getX(), evt.getY());
                if(element != null)
                {
                     lastNode = element;
                }
                
                if (SwingUtilities.isLeftMouseButton(evt)) 
                {
                    super.mousePressed(evt);
                }
                
                this.last = null;
            }
            
            @Override
            public void mouseMoved(MouseEvent evt)
            {                
                // Disply log message to show what locomotives are excluded
                if (!parent.getModel().getAutoLayout().isRunning())
                {
                    g.requestFocus();
                    
                    GraphicElement element = view.findGraphicElementAt(EnumSet.of(InteractiveElement.NODE), evt.getX(), evt.getY());

                    if (element != null)
                    {
                        Point p = (Point) parent.getModel().getAutoLayout().getPointById(element.getId());

                        if (p != null)
                        {                            
                            if (!p.getName().equals(lastHoveredNode))
                            {
                                // Last hovered node will be used by key listener, so put this condition here instead of above
                                if (parent.isShowStationLengthsSelected())
                                {
                                    List<String> locomotiveNames = p.getExcludedLocs().stream()
                                        .map(Locomotive::getName)
                                        .collect(Collectors.toList());

                                    if (!locomotiveNames.isEmpty())
                                    {
                                        ui.getModel().log("Excluded at " + p.getName() + ": " + locomotiveNames.toString().replace("[", "").replace("]", ""));
                                    }
                                }
                                
                                lastHoveredNode = p.getName();
                            }
                        }
                    }
                    else
                    {
                        lastHoveredNode = null;
                    }
                }
            }
            
            /**
             * Support right click menus for nodes (and eventually edges)
             */
            @Override
            public void mouseClicked(MouseEvent evt)
            {
                // Reset view with middle button
                if ((evt.getModifiers() & InputEvent.BUTTON2_MASK) != 0)
                {
                    swingView.getCamera().setViewPercent(1);
                    swingView.getCamera().setViewCenter(
                        swingView.getCamera().getMetrics().graphWidthGU() / 2 + 200, 
                        swingView.getCamera().getMetrics().graphHeightGU() / 2 - 100, // based on padding
                        0
                    );
                }
                else
                { 
                    if (!parent.getModel().getAutoLayout().isRunning())
                    {
                        // Special double-click functionality - directly edit the locomotive
                        if (!SwingUtilities.isRightMouseButton(evt) && evt.getClickCount() == 2)
                        {
                            GraphicElement element = view.findGraphicElementAt(EnumSet.of(InteractiveElement.NODE), evt.getX(), evt.getY());

                            if (element != null)
                            {
                                Point p = (Point) parent.getModel().getAutoLayout().getPointById(element.getId());

                                if (p != null && p.isDestination() && !parent.getModel().getLocomotives().isEmpty())
                                {    
                                    // Select the active locomotive
                                    GraphLocAssign edit = new GraphLocAssign(parent, p, 
                                        // If no locs in list, add new
                                        parent.getModel().getAutoLayout().getLocomotivesToRun().isEmpty()
                                    );

                                    int dialogResult = JOptionPane.showConfirmDialog(
                                        (Component) swingView, edit, 
                                        !parent.getModel().getAutoLayout().getLocomotivesToRun().isEmpty() ? "Edit / Assign Locomotive" : "Place New Locomotive", 
                                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
                                    );

                                    if (dialogResult == JOptionPane.OK_OPTION)
                                    {
                                        edit.commitChanges();
                                    }
                                }
                            }   
                        }
                        else if (SwingUtilities.isRightMouseButton(evt))
                        {
                            GraphicElement element = view.findGraphicElementAt(EnumSet.of(InteractiveElement.NODE), evt.getX(), evt.getY());

                            if (element != null)
                            {
                                Point p = (Point) parent.getModel().getAutoLayout().getPointById(element.getId());

                                if (p != null)
                                {                         
                                    RightClickMenu menu = new RightClickMenu(parent, p);

                                    menu.show(evt.getComponent(), evt.getX(), evt.getY()); 
                                }
                            }  
                            else
                            {
                                // Right click on edges does not currently work, so all edge related options will be on the point right click menu
                                // Insert at cursor
                                Point3 position = view.getCamera().transformPxToGu(evt.getX(), evt.getY());

                                RightClickMenuNew menu = new RightClickMenuNew(parent, (int) position.x, (int) position.y, false);
                                menu.show(evt.getComponent(), evt.getX(), evt.getY());  
                            }           
                        }    
                    }
                    else
                    {
                        Point3 position = view.getCamera().transformPxToGu(evt.getX(), evt.getY());

                        RightClickMenuNew menu = new RightClickMenuNew(parent, (int) position.x, (int) position.y, true);
                        menu.show(evt.getComponent(), evt.getX(), evt.getY()); 
                    }
                }
            }
        });
                
        // Set custom key listener
        swingView.setShortcutManager(new DefaultShortcutManager()
        {
            private View viewui;

            @Override
            public void init(GraphicGraph graph, View view)
            {
                this.viewui = view;
                view.addListener("Key", this);
            }

            @Override
            public void release()
            {
                viewui.removeListener("Key", this);
            }
            
            @Override
            public void keyPressed(KeyEvent e)
            {                     
                // Pass event to main UI
                parent.childWindowKeyEvent(e);
            }

            @Override
            public void keyReleased(KeyEvent e) { }

            @Override
            public void keyTyped(KeyEvent e) { }
        });
        
        // Render window
        initComponents(); 
        getContentPane().add((Component) swingView);
        
        setLocationRelativeTo(parent); // center
        pack();
        setVisible(true);
        
        setAlwaysOnTop(parent.isAlwaysOnTop());
        requestFocus();
        toFront();
        
        // Remember window location
        this.loadWindowBounds();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setTitle(WINDOW_TITLE);
        setIconImage(java.awt.Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setMaximumSize(new java.awt.Dimension(2000, 2000));
        setMinimumSize(new java.awt.Dimension(400, 400));
        setPreferredSize(new java.awt.Dimension(600, 600));
        setSize(new java.awt.Dimension(600, 572));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                formKeyPressed(evt);
            }
        });
    }// </editor-fold>//GEN-END:initComponents

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        
        // Special key commands for the graph UI
        
        int keyCode = evt.getKeyCode();
        // boolean altPressed = (evt.getModifiers() & KeyEvent.ALT_MASK) != 0;
        boolean controlPressed = (evt.getModifiers() & KeyEvent.CTRL_MASK) != 0 || (evt.getModifiers() & KeyEvent.CTRL_DOWN_MASK) != 0;
        
        boolean isRunning = parent.getModel().getAutoLayout().isRunning();
        
        if (!isRunning && controlPressed && keyCode == KeyEvent.VK_V)
        {
            // can also be parent.getCopyTarget()
            if (parent.getActiveLoc() != null && this.lastHoveredNode != null)
            {
                parent.getModel().getAutoLayout().moveLocomotive(this.clipboard != null ? this.clipboard.getName() : parent.getActiveLoc().getName(), this.lastHoveredNode, false);
                this.clipboard = null;
                parent.repaintAutoLocList(false);
            }
        }
        else if (!isRunning && (keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE || controlPressed && keyCode == KeyEvent.VK_X))
        {
            if (this.lastHoveredNode != null)
            {
                this.clipboard = null;
                if (controlPressed && keyCode == KeyEvent.VK_X)
                {
                    this.clipboard = parent.getModel().getAutoLayout().getPoint(this.lastHoveredNode).getCurrentLocomotive();
                }
                
                parent.getModel().getAutoLayout().moveLocomotive(null, this.lastHoveredNode, true);
                parent.repaintAutoLocList(false);
            }
        }
        else if (!isRunning && controlPressed && (keyCode == KeyEvent.VK_E || keyCode == KeyEvent.VK_U))
        {
            if (parent.getActiveLoc() != null && this.lastHoveredNode != null)
            {
                Point p = parent.getModel().getAutoLayout().getPoint(this.lastHoveredNode);
                
                if (p != null)
                {
                    if (keyCode == KeyEvent.VK_E)
                    {
                        p.getExcludedLocs().add(parent.getActiveLoc()); 
                    }
                    else if (keyCode == KeyEvent.VK_U)
                    {
                        p.getExcludedLocs().remove(parent.getActiveLoc()); 
                    }
                    
                    parent.updatePoint(p, mainGraph);
                    parent.repaintAutoLocList(true);
                }
            }
        }
        // Configure S88
        else if (!isRunning && controlPressed && (keyCode == KeyEvent.VK_S))
        {
            this.setS88(parent.getModel().getAutoLayout().getPoint(this.lastHoveredNode));
        }
        // Default key commands
        else
        {
            parent.childWindowKeyEvent(evt);
        }
    }//GEN-LAST:event_formKeyPressed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        parent.greyOutAutonomy();
    }//GEN-LAST:event_formWindowClosing

    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
