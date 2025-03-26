package org.traincontrol.gui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JPanel;
import org.traincontrol.marklin.MarklinLayout;
import org.traincontrol.marklin.MarklinLayoutComponent;

/**
 * Work in progress - track diagram editor
 * @author Adam
 */
public class LayoutEditor extends PositionAwareJFrame
{
    private final TrainControlUI parent;
    private final int size;
    private final MarklinLayout layout;
    private LayoutGrid grid;
    
    /**
     * Popup window showing train layouts
     * @param l reference to the layout
     * @param size size of each tile, in pixels
     * @param ui
     * @param pageIndex
     */
    public LayoutEditor(MarklinLayout l, int size, TrainControlUI ui, int pageIndex)
    {
        initComponents();
        
        this.ExtLayoutPanel.setLayout(new FlowLayout());
        this.parent = ui;
        this.size = size;
        this.layout = l;
    }
    
    public void receiveEvent(MouseEvent e, LayoutLabel x)
    {
        System.out.println(x);
        
        System.out.println(Arrays.toString(grid.getCoordinates(x)));
        
        MarklinLayoutComponent lc = layout.getComponent(grid.getCoordinates(x)[0], grid.getCoordinates(x)[1]);
        
        lc.rotate();
        
        try
        {
            layout.addComponent(lc, grid.getCoordinates(x)[0], grid.getCoordinates(x)[1]);
        }
        catch (IOException ex)
        {
            
        }
        
        drawGrid();
    }
    
    private void drawGrid()
    {
        this.ExtLayoutPanel.removeAll();

        grid = new LayoutGrid(this.layout, size,
            this.ExtLayoutPanel, 
            this,
            true, parent, true);
        
        setTitle(this.layout.getName() + this.parent.getWindowTitleString());

        // Scale the popup according to the size of the layout
        if (!this.isLoaded())
        {
            this.setPreferredSize(new Dimension(grid.maxWidth + 100, grid.maxHeight + 100));
            pack();
        }
        
        // Remember window location for different layouts and sizes
        this.setWindowIndex(this.layout.getName()+ "_editor_" + this.getLayoutSize());
        
        // Only load location once
        if (!this.isLoaded())
        {
            loadWindowBounds();
        }
        
        saveWindowBounds();
        
        // Update auto layout station labels on the track diagram
        this.parent.updateVisiblePoints();
    }
    
    public String getLayoutTitle()
    {
        return this.layout.getName();
    }
    
    public void render()
    {        
        this.setAlwaysOnTop(parent.isAlwaysOnTop());
                      
        drawGrid();
             
        setVisible(true);
        
        // Hide the window on close so that LayoutLabels know they can be deleted
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                e.getComponent().setVisible(false);
            }
        });
    }
      
    public JPanel getPanel()
    {
        return this.ExtLayoutPanel;
    }
    
    public int getLayoutSize()
    {
        return this.size;
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
        jPanel1 = new javax.swing.JPanel();

        setIconImage(Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setMinimumSize(new java.awt.Dimension(150, 150));
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                formKeyPressed(evt);
            }
        });

        ExtLayoutPanel.setBackground(new java.awt.Color(255, 255, 255));

        javax.swing.GroupLayout ExtLayoutPanelLayout = new javax.swing.GroupLayout(ExtLayoutPanel);
        ExtLayoutPanel.setLayout(ExtLayoutPanelLayout);
        ExtLayoutPanelLayout.setHorizontalGroup(
            ExtLayoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 750, Short.MAX_VALUE)
        );
        ExtLayoutPanelLayout.setVerticalGroup(
            ExtLayoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 431, Short.MAX_VALUE)
        );

        jScrollPane1.setViewportView(ExtLayoutPanel);

        jPanel1.setBackground(new java.awt.Color(255, 255, 255));

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 182, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 675, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 446, Short.MAX_VALUE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        parent.childWindowKeyEvent(evt);
    }//GEN-LAST:event_formKeyPressed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel ExtLayoutPanel;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables
}
