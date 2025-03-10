package org.traincontrol.gui;

import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
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
 
    /**
     * Called externally - will return null if the window is hidden
     * @return 
     */
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
    public void setS88(Point p)
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
                        
                            setLastClickedNode(parent.getModel().getAutoLayout().getPointById(node.getId()).getName());
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
                            if (!p.getName().equals(getLastHoveredNode()))
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
                                
                                setLastHoveredNode(p.getName());
                            }
                        }
                    }
                    else
                    {
                        setLastHoveredNode(null);
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
                                    GraphRightClickPointMenu menu = new GraphRightClickPointMenu(parent, p, g);

                                    menu.show(evt.getComponent(), evt.getX(), evt.getY()); 
                                }
                            }  
                            else
                            {
                                // Right click on edges does not currently work, so all edge related options will be on the point right click menu
                                // Insert at cursor
                                Point3 position = view.getCamera().transformPxToGu(evt.getX(), evt.getY());

                                GraphRightClickGeneralMenu menu = new GraphRightClickGeneralMenu(parent, (int) position.x, (int) position.y, false, g);
                                menu.show(evt.getComponent(), evt.getX(), evt.getY());  
                            }           
                        }    
                    }
                    else
                    {
                        Point3 position = view.getCamera().transformPxToGu(evt.getX(), evt.getY());

                        GraphRightClickGeneralMenu menu = new GraphRightClickGeneralMenu(parent, (int) position.x, (int) position.y, true, g);
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
    
    // Getters and setters
    
    public GraphEdgeEdit getGraphEdgeEdit()
    {
        return graphEdgeEdit;
    }

    public void setGraphEdgeEdit(GraphEdgeEdit graphEdgeEdit)
    {
        this.graphEdgeEdit = graphEdgeEdit;
    }
    
    public String getLastHoveredNode()
    {
        return lastHoveredNode;
    }

    public void setLastHoveredNode(String lastHoveredNode)
    {
        this.lastHoveredNode = lastHoveredNode;
    }

    public String getLastClickedNode()
    {
        return lastClickedNode;
    }

    public void setLastClickedNode(String lastClickedNode)
    {
        this.lastClickedNode = lastClickedNode;
    }
    
    public Graph getMainGraph()
    {
        return mainGraph;
    }
    
    public View getSwingView()
    {
        return swingView;
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
            if (parent.getActiveLoc() != null && this.getLastHoveredNode() != null)
            {
                parent.getModel().getAutoLayout().moveLocomotive(this.clipboard != null ? this.clipboard.getName() : parent.getActiveLoc().getName(), this.lastHoveredNode, false);
                this.clipboard = null;
                parent.repaintAutoLocList(false);
            }
        }
        else if (!isRunning && (keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE || controlPressed && keyCode == KeyEvent.VK_X))
        {
            if (this.getLastHoveredNode() != null)
            {
                this.clipboard = null;
                if (controlPressed && keyCode == KeyEvent.VK_X)
                {
                    this.clipboard = parent.getModel().getAutoLayout().getPoint(this.getLastHoveredNode()).getCurrentLocomotive();
                }
                
                parent.getModel().getAutoLayout().moveLocomotive(null, this.getLastHoveredNode(), true);
                parent.repaintAutoLocList(false);
            }
        }
        else if (!isRunning && controlPressed && (keyCode == KeyEvent.VK_E || keyCode == KeyEvent.VK_U))
        {
            if (parent.getActiveLoc() != null && this.getLastHoveredNode() != null)
            {
                Point p = parent.getModel().getAutoLayout().getPoint(this.getLastHoveredNode());
                
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
            this.setS88(parent.getModel().getAutoLayout().getPoint(this.getLastHoveredNode()));
        }
        // Default key commands
        else
        {
            parent.childWindowKeyEvent(evt);
        }
    }//GEN-LAST:event_formKeyPressed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        parent.greyOutAutonomy();
        
        // Easy way to clear out the track diagram on demand
        parent.resetLayoutStationLabels();
    }//GEN-LAST:event_formWindowClosing

    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
