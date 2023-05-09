/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import org.graphstream.graph.Graph;
import org.graphstream.ui.graphicGraph.GraphicGraph;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.swing_viewer.util.DefaultShortcutManager;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.View;

/**
 *
 * @author Adam
 */
final public class GraphViewer extends javax.swing.JFrame {
    
    TrainControlUI parent;
    SwingViewer swingViewer;
    /**
     * Creates new form GraphViewer
     * @param graph
     * @param ui
     */
    public GraphViewer(Graph graph, TrainControlUI ui)
    {
        parent = ui;
                
        // Initialize viewer   
        swingViewer = new SwingViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
        View view = swingViewer.addDefaultView(false);
        swingViewer.enableAutoLayout();
        
        // Set custom key listener
        view.setShortcutManager(new DefaultShortcutManager() {

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
        getContentPane().add((Component) view);
             
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
        setIconImage(Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setMinimumSize(new java.awt.Dimension(100, 100));
        setPreferredSize(new java.awt.Dimension(900, 572));
        setSize(new java.awt.Dimension(600, 572));
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
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

    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
