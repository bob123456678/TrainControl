/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JPanel;
import marklin.MarklinLayout;

/**
 *
 * @author Adam
 */
public class LayoutPopupUI extends javax.swing.JFrame {

    TrainControlUI parent;
    int size;
    MarklinLayout layout;
    
    /**
     * Popup window showing train layouts
     * @param l reference to the layout
     * @param size size of each tile, in pixels
     * @param ui
     */
    public LayoutPopupUI(MarklinLayout l, int size, TrainControlUI ui)
    {
        initComponents();
        
        this.ExtLayoutPanel.setLayout(new FlowLayout());
        this.parent = ui;
        this.size = size;
        this.layout = l;
    }
    
    private void drawGrid()
    {
        this.ExtLayoutPanel.removeAll();

        LayoutGrid grid = new LayoutGrid(this.layout, size,
            this.ExtLayoutPanel, 
            this,
            true, parent);
        
        setTitle(this.layout.getName());

        // Scale the popup according to the size of the layout
        this.setPreferredSize(new Dimension(grid.maxWidth + 100, grid.maxHeight + 100));
    }
    
    public void render()
    {        
        this.setAlwaysOnTop(true);
                      
        drawGrid();
        
        pack();
     
        setVisible(true);
        
        // Hide the window on close so that LayoutLabels know they can be deleted
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                e.getComponent().setVisible(false);
            }
          }
        );
    }
    
    /**
     * Updates the layout page
     * @param index
     */
    public void goToLayoutPage(int index)
    {
        int page = index + 1;
     
        if (index < this.parent.getModel().getLayoutList().size() && index >= 0)
        {
            this.parent.getModel().log("Popup layout jumping to page " + page);

            this.layout = this.parent.getModel().getLayout(this.parent.getModel().getLayoutList().get(index));
            
            drawGrid();  
            this.repaint();
        }
        else
        {
            this.parent.getModel().log("Popup layout page " + page + " does not exist");
        }
    }
    
    public JPanel getPanel()
    {
        return this.ExtLayoutPanel;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        ExtLayoutPanel = new javax.swing.JPanel();

        setIconImage(Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setPreferredSize(new java.awt.Dimension(800, 600));
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                formKeyPressed(evt);
            }
        });

        ExtLayoutPanel.setBackground(new java.awt.Color(255, 255, 255));
        ExtLayoutPanel.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                ExtLayoutPanelKeyPressed(evt);
            }
        });

        javax.swing.GroupLayout ExtLayoutPanelLayout = new javax.swing.GroupLayout(ExtLayoutPanel);
        ExtLayoutPanel.setLayout(ExtLayoutPanelLayout);
        ExtLayoutPanelLayout.setHorizontalGroup(
            ExtLayoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 368, Short.MAX_VALUE)
        );
        ExtLayoutPanelLayout.setVerticalGroup(
            ExtLayoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 266, Short.MAX_VALUE)
        );

        jScrollPane1.setViewportView(ExtLayoutPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void ExtLayoutPanelKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_ExtLayoutPanelKeyPressed
        
    }//GEN-LAST:event_ExtLayoutPanelKeyPressed

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        parent.childWindowKeyEvent(evt);
    }//GEN-LAST:event_formKeyPressed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel ExtLayoutPanel;
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables
}
