/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import automation.Point;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.EnumSet;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.algorithm.Toolkit;
import org.graphstream.ui.geom.Point3;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.graphicGraph.GraphicGraph;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.swing_viewer.util.DefaultMouseManager;
import org.graphstream.ui.swing_viewer.util.DefaultShortcutManager;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.View;
import org.graphstream.ui.view.util.InteractiveElement;

/**
 *
 * @author Adam
 */
final public class GraphViewer extends javax.swing.JFrame {
    
    TrainControlUI parent;
    SwingViewer swingViewer;
    View swingView;
    
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
                                parent.getModel().getAutoLayout().moveLocomotive(edit.getLoc(), nodeName, false);

                                parent.getModel().getLocByName(edit.getLoc()).setReversible(edit.isReversible());
                                parent.getModel().getLocByName(edit.getLoc()).setArrivalFunc(edit.getArrivalFunc());
                                parent.getModel().getLocByName(edit.getLoc()).setDepartureFunc(edit.getDepartureFunc());
                                parent.getModel().getLocByName(edit.getLoc()).setPreferredSpeed(edit.getSpeed());
                                parent.getModel().getLocByName(edit.getLoc()).setTrainLength(edit.getTrainLength());

                                parent.repaintAutoLocList();
                            }
                        }
                    );    

                    add(menuItem);
                }

                menuItem = new JMenuItem("Add New Locomotive to Graph at " + nodeName);
                menuItem.addActionListener(event -> 
                    {
                        GraphLocAssign edit = new GraphLocAssign(parent, p, true);

                        int dialogResult = JOptionPane.showConfirmDialog((Component) swingView, edit, "Place New Locomotive", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                        if(dialogResult == JOptionPane.OK_OPTION)
                        {
                            parent.getModel().getAutoLayout().moveLocomotive(edit.getLoc(), nodeName, false);

                            parent.getModel().getLocByName(edit.getLoc()).setReversible(edit.isReversible());
                            parent.getModel().getLocByName(edit.getLoc()).setArrivalFunc(edit.getArrivalFunc());
                            parent.getModel().getLocByName(edit.getLoc()).setDepartureFunc(edit.getDepartureFunc());
                            parent.getModel().getLocByName(edit.getLoc()).setPreferredSpeed(edit.getSpeed());
                            parent.getModel().getLocByName(edit.getLoc()).setTrainLength(edit.getTrainLength());

                            parent.repaintAutoLocList();
                        }
                    }
                ); 

                add(menuItem);
            
                menuItem = new JMenuItem("Set Maximum Train Length at " + nodeName + " (" + (p.getMaxTrainLength() != 0 ? p.getMaxTrainLength() : "any") + ")");
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
                                parent.repaintAutoLocList();
                            }
                            catch (Exception e)
                            {
                                JOptionPane.showMessageDialog((Component) swingView,
                                    "Invalid value (must be a positive integer, or 0 to disable)");
                            }
                        }
                    }
                );     
            
                add(menuItem);

                addSeparator();
            }

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
                                parent.repaintAutoLocList();
                            }

                        }
                        catch (Exception e)
                        {
                            JOptionPane.showMessageDialog((Component) swingView,
                                "Error renaming node.");
                        }
                    }
                }
            );  
                
            add(menuItem);
            
            if (p.isDestination())
            {                 
                menuItem = new JMenuItem("Set as " + (p.isTerminus() ? "Non-terminus" : "Terminus") + " station");
                menuItem.addActionListener(event -> { 
                    try
                    { 
                        p.setTerminus(!p.isTerminus());
                        parent.getModel().getAutoLayout().refreshUI();
                        parent.repaintAutoLocList(); 
                    } catch (Exception ex) {}
                });
                       
                add(menuItem);
            }
            
            if (p.isOccupied() && p.isDestination())
            {
                addSeparator();

                menuItem = new JMenuItem("Remove Locomotive " + p.getCurrentLocomotive().getName() + " from Node");
                menuItem.addActionListener(event -> { parent.getModel().getAutoLayout().moveLocomotive(null, nodeName, false); parent.repaintAutoLocList();});    
                add(menuItem);

                addSeparator();

                menuItem = new JMenuItem("Remove Locomotive " + p.getCurrentLocomotive().getName() + " from Graph");
                menuItem.addActionListener(event -> { parent.getModel().getAutoLayout().moveLocomotive(null, nodeName, true); parent.repaintAutoLocList(); });    
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
        
        // swingViewer.getDefaultView().enableMouseOptions();

        if (autoLayout)
        {
            swingViewer.enableAutoLayout();    
        }
        else
        {
            swingViewer.disableAutoLayout();
        }
        
        // Disable the auto layout if a node gets dragged
        swingView.setMouseManager(new DefaultMouseManager() {

            GraphicElement lastNode;
            
            @Override
            public void mouseReleased(MouseEvent evt)
            {
                if (autoLayout)
                {
                    swingViewer.disableAutoLayout();
                }
                
                if (SwingUtilities.isLeftMouseButton(evt))
                {
                    int maxY =  0;
                    
                    for (Object o : swingViewer.getGraphicGraph().nodes().toArray())
                    {
                        Node node = (Node) o;
                        Point3 position = view.getCamera().transformGuToPx(Toolkit.nodePosition(node)[0], Toolkit.nodePosition(node)[1], 0);
                        
                        if (new Double(position.y).intValue() > maxY)
                        {
                            maxY = new Double(position.y).intValue();
                        }
                    }
                    
                    final int maxYY = maxY;
                
                    GraphicElement element = view.findGraphicElementAt(EnumSet.of(InteractiveElement.NODE), evt.getX(), evt.getY());
                    
                    // The above sometimes fails if the element has the lowest Y value.  Use stored element instead.
                    if (element == null)
                    {
                        element = lastNode;
                    }
                    
                    if(element != null)
                    {
                        Node node = swingViewer.getGraphicGraph().getNode(element.getId());
                        
                        Point3 position = view.getCamera().transformGuToPx(Toolkit.nodePosition(node)[0], Toolkit.nodePosition(node)[1], 0);
                        
                        parent.getModel().getAutoLayout().getPointById(node.getId()).setX(new Double(position.x).intValue());
                        parent.getModel().getAutoLayout().getPointById(node.getId()).setY(maxYY - new Double(position.y).intValue());
                        
                        parent.getModel().log("Moved " + parent.getModel().getAutoLayout().getPointById(node.getId()).getName() + " to " + new Double(position.x).intValue() + "," + (maxYY - new Double(position.y).intValue()));
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
            }
            
            /**
             * Support right click menus for nodes (and eventually edges)
             */
            @Override
            public void mouseClicked(MouseEvent evt)
            {
                //if (evt.getClickCount() == 2)
                //{
                    if (SwingUtilities.isRightMouseButton(evt) 
                            && !parent.getModel().getAutoLayout().isRunning())
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
                    }
                //}    
            }
        });
                
        // Set custom key listener
        swingView.setShortcutManager(new DefaultShortcutManager() {

            private View view;

            @Override
            public void init(GraphicGraph graph, View view) {
                this.view = view;
                view.addListener("Key", this);
            }

            @Override
            public void release() {
                view.removeListener("Key", this);
            }
            
            @Override
            public void keyPressed(KeyEvent e) {
                                
                // Print out coordinates of each node to assist with making the JSON file
                if (e.getKeyCode() == KeyEvent.VK_C)
                {
                    int maxY =  0;
                    
                    for (Object o : swingViewer.getGraphicGraph().nodes().toArray())
                    {
                        Node node = (Node) o;
                        Point3 position = view.getCamera().transformGuToPx(Toolkit.nodePosition(node)[0], Toolkit.nodePosition(node)[1], 0);
                        
                        if (new Double(position.y).intValue() > maxY)
                        {
                            maxY = new Double(position.y).intValue();
                        }
                    }
                    
                    final int maxYY = maxY;
                
                    swingViewer.getGraphicGraph().nodes().forEach((node) -> {
                        Point3 position = view.getCamera().transformGuToPx(Toolkit.nodePosition(node)[0], Toolkit.nodePosition(node)[1], 0);
                        parent.getModel().log(node.getId() + "\n \"x\" : " + new Double(position.x).intValue() + ",\n \"y\" : " + (maxYY - new Double(position.y).intValue()) + "\n");
                        parent.getModel().getAutoLayout().getPoint(node.getId()).setX(new Double(position.x).intValue());
                        parent.getModel().getAutoLayout().getPoint(node.getId()).setY(maxYY - new Double(position.y).intValue());
                    });  
                }
                
                parent.childWindowKeyEvent(e);
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }

            @Override
            public void keyTyped(KeyEvent e) {
            }
        });
        
        // Render window
        initComponents(); 
        getContentPane().add((Component) swingView);
        
        setLocationRelativeTo(parent); // center
        pack();
        setVisible(true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Auto Layout Graph View");
        setAlwaysOnTop(true);
        setIconImage(java.awt.Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setMaximumSize(new java.awt.Dimension(2000, 2000));
        setMinimumSize(new java.awt.Dimension(400, 400));
        setSize(new java.awt.Dimension(600, 572));
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
        });
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
        parent.childWindowKeyEvent(evt);
    }//GEN-LAST:event_formKeyPressed

    private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
        

    }//GEN-LAST:event_formComponentResized

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        parent.greyOutAutonomy();
    }//GEN-LAST:event_formWindowClosing

    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
